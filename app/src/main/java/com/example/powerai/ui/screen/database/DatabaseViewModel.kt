package com.example.powerai.ui.screen.database

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.util.PLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseFocusTarget
import com.example.powerai.domain.model.DatabaseRow
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DatabaseUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val groups: List<DatabaseFileGroup> = emptyList(),
    val currentQuery: String = "",
    val collapsedGroupKeys: Set<String> = emptySet(),
    val focusTarget: DatabaseFocusTarget? = null,
    val focusGroupKey: String? = null,
    val selectedItemId: Long? = null,
    val pendingSelectedItemScrollId: Long? = null,
    val searchHistory: List<com.example.powerai.domain.model.SearchEntry> = emptyList(),
    val sourceFileNames: Map<Long, String> = emptyMap(),
    val importProgress: com.example.powerai.data.importer.ImportProgress? = null,
    val importDiagnostics: com.example.powerai.data.importer.AssetImportDiagnostics? = null
)

@HiltViewModel
class DatabaseViewModel @Inject constructor(
    private val useCase: com.example.powerai.domain.usecase.DatabaseUseCase,
    private val importManager: com.example.powerai.data.importer.DocumentImportManager,
    private val savedStateHandle: SavedStateHandle
) : BaseMviViewModel<DatabaseIntent, DatabaseUiState, Nothing>(
    initialState = DatabaseUiState(
        currentQuery = savedStateHandle[KEY_CURRENT_QUERY] ?: "",
        collapsedGroupKeys = savedStateHandle.get<ArrayList<String>>(KEY_COLLAPSED_GROUP_KEYS)?.toSet().orEmpty()
    )
) {

    private companion object {
        private const val TAG = "PowerAiDbDebug"
        const val KEY_CURRENT_QUERY = "database_current_query"
        const val KEY_COLLAPSED_GROUP_KEYS = "database_collapsed_group_keys"
    }

    override fun onIntent(intent: DatabaseIntent) {
        when (intent) {
            is DatabaseIntent.Refresh -> reloadCurrentContent()
            is DatabaseIntent.LoadAll -> loadAll()
            is DatabaseIntent.EnsureLoaded -> ensureLoaded()
            is DatabaseIntent.Search -> search(intent.query)
            is DatabaseIntent.FocusItem -> focusItem(intent.itemId)
            is DatabaseIntent.FocusItemWithDetails -> focusItem(intent.item)
            is DatabaseIntent.ConsumeFocusTarget -> consumeFocusTarget()
            is DatabaseIntent.FocusGroup -> focusGroup(intent.groupKey)
            is DatabaseIntent.FocusGroupFromDrawer -> focusGroupFromDrawer(intent.groupKey)
            is DatabaseIntent.ConsumeFocusGroup -> consumeFocusGroup()
            is DatabaseIntent.SelectItem -> setSelectedItem(intent.itemId)
            is DatabaseIntent.ConsumePendingSelectedItemScroll -> consumePendingSelectedItemScroll()
            is DatabaseIntent.ToggleCollapsedGroup -> toggleCollapsedGroupKey(intent.key)
            is DatabaseIntent.SetCollapsedGroupKeys -> setCollapsedGroupKeys(intent.keys)
            is DatabaseIntent.ClearSearchHistory -> clearSearchHistory()
            is DatabaseIntent.RefreshImportDiagnostics -> refreshImportDiagnostics()
        }
    }

    private val _directoryGroups = MutableStateFlow<List<DatabaseFileGroup>>(emptyList())
    val directoryGroups: StateFlow<List<DatabaseFileGroup>> = _directoryGroups.asStateFlow()

    val importProgress: StateFlow<com.example.powerai.data.importer.ImportProgress?> = importManager.progress
    val importDiagnostics: StateFlow<com.example.powerai.data.importer.AssetImportDiagnostics> = importManager.importDiagnostics

    private var hasLoadedContent: Boolean = false
    private var lastCompletedImportSignature: String? = null

    fun refresh() {
        reloadCurrentContent()
    }

    fun refreshImportDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            importManager.refreshImportDiagnostics()
        }
    }

    fun loadAll() {
        updateCurrentQuery("")
        loadAllInternal()
    }

    fun ensureLoaded() {
        if (hasLoadedContent) return
        reloadCurrentContent()
    }

    fun reloadCurrentContent() {
        val query = currentState.currentQuery.trim()
        if (query.isBlank()) {
            loadAllInternal()
        } else {
            searchInternal(query, addToHistory = false)
        }
    }

    fun search(rawQuery: String) {
        val query = rawQuery.trim()
        updateCurrentQuery(query)
        if (query.isBlank()) {
            loadAllInternal()
            return
        }

        searchInternal(query, addToHistory = true)
    }

    private fun loadAllInternal() {
        hasLoadedContent = true
        viewModelScope.launch(Dispatchers.IO) {
            PLog.d(TAG, "loadAllInternal start query=${currentState.currentQuery} directoryGroups=${_directoryGroups.value.size}")
            updateState { copy(isLoading = true, errorMessage = null) }
            try {
                var groups = useCase.loadAll()
                PLog.d(TAG, "loadAllInternal first load groups=${groups.size}")
                if (groups.isEmpty()) {
                    PLog.w(TAG, "loadAllInternal got empty groups, triggering importAssetsIfNeed")
                    runCatching { importManager.importAssetsIfNeed() }
                    groups = useCase.loadAll()
                    PLog.d(TAG, "loadAllInternal after import retry groups=${groups.size}")
                }
                if (groups.isNotEmpty() || _directoryGroups.value.isEmpty()) {
                    _directoryGroups.value = groups
                    PLog.d(TAG, "loadAllInternal updated directoryGroups=${_directoryGroups.value.size}")
                }
                updateState { copy(isLoading = false, groups = groups, errorMessage = null) }
                PLog.d(TAG, "loadAllInternal finish uiGroups=${groups.size} error=null")
            } catch (t: Throwable) {
                PLog.e(TAG, "loadAllInternal failed", t)
                updateState { copy(isLoading = false, errorMessage = t.message ?: "加载失败") }
            }
        }
    }

    private fun searchInternal(query: String, addToHistory: Boolean) {
        hasLoadedContent = true
        if (addToHistory) addSearchHistory(query)
        viewModelScope.launch(Dispatchers.IO) {
            PLog.d(TAG, "searchInternal start query=$query addToHistory=$addToHistory directoryGroups=${_directoryGroups.value.size}")
            updateState { copy(isLoading = true, errorMessage = null) }
            try {
                val groups = useCase.search(query)
                PLog.d(TAG, "searchInternal search groups=${groups.size} query=$query")
                if (_directoryGroups.value.isEmpty()) {
                    var directoryGroups = runCatching { useCase.loadAll() }.getOrDefault(emptyList())
                    PLog.d(TAG, "searchInternal backfill first load directoryGroups=${directoryGroups.size}")
                    if (directoryGroups.isEmpty()) {
                        PLog.w(TAG, "searchInternal backfill empty, triggering importAssetsIfNeed")
                        runCatching { importManager.importAssetsIfNeed() }
                        directoryGroups = runCatching { useCase.loadAll() }.getOrDefault(emptyList())
                        PLog.d(TAG, "searchInternal backfill after import directoryGroups=${directoryGroups.size}")
                    }
                    if (directoryGroups.isNotEmpty()) {
                        _directoryGroups.value = directoryGroups
                        PLog.d(TAG, "searchInternal updated directoryGroups=${_directoryGroups.value.size}")
                    }
                }
                updateState { copy(isLoading = false, groups = groups, errorMessage = null) }
                PLog.d(TAG, "searchInternal finish uiGroups=${groups.size} query=$query")
            } catch (t: Throwable) {
                PLog.e(TAG, "searchInternal failed query=$query", t)
                updateState { copy(isLoading = false, errorMessage = t.message ?: "搜索失败") }
            }
        }
    }

    private fun updateCurrentQuery(query: String) {
        updateState { copy(currentQuery = query) }
        savedStateHandle[KEY_CURRENT_QUERY] = query
    }

    fun focusItem(itemId: Long) {
        if (itemId <= 0L) return
        updateState { copy(selectedItemId = itemId, pendingSelectedItemScrollId = itemId, focusTarget = DatabaseFocusTarget(itemId)) }
        updateCurrentQuery("")
        loadAllInternal()
    }

    fun focusItem(item: KnowledgeItem) {
        if (item.id <= 0L) return
        updateState {
            copy(
                selectedItemId = item.id,
                pendingSelectedItemScrollId = item.id,
                focusTarget = DatabaseFocusTarget(
                    itemId = item.id,
                    title = item.title,
                    source = item.contextLabel ?: item.source,
                    pageNumber = item.pageNumber,
                    content = item.content
                )
            )
        }
        prefetchSourceFileNames(listOf(item))
        updateCurrentQuery("")
        loadAllInternal()
    }

    fun consumeFocusTarget() {
        updateState { copy(focusTarget = null) }
    }

    fun focusGroup(groupKey: String) {
        val normalized = groupKey.trim()
        if (normalized.isBlank()) return
        updateState { copy(selectedItemId = null, pendingSelectedItemScrollId = null, focusTarget = null) }
        val currentKeys = currentState.groups.map { it.key }.toSet()
        if (currentKeys.isNotEmpty() && currentKeys.contains(normalized)) {
            setCollapsedGroupKeys(currentKeys - normalized)
        }
        updateState { copy(focusGroupKey = normalized) }
    }

    fun focusGroupFromDrawer(groupKey: String) {
        val normalized = groupKey.trim()
        if (normalized.isBlank()) return
        PLog.d(TAG, "focusGroupFromDrawer groupKey=$normalized directoryGroups=${_directoryGroups.value.size} uiGroups=${currentState.groups.size}")
        updateState { copy(selectedItemId = null, pendingSelectedItemScrollId = null, focusTarget = null) }
        updateCurrentQuery("")

        val directoryGroups = _directoryGroups.value
        if (directoryGroups.isNotEmpty()) {
            val groupKeys = directoryGroups.map { it.key }.toSet()
            updateState { copy(isLoading = false, groups = directoryGroups, errorMessage = null) }
            PLog.d(TAG, "focusGroupFromDrawer restored directoryGroups=${directoryGroups.size} containsTarget=${groupKeys.contains(normalized)}")
            if (groupKeys.contains(normalized)) {
                setCollapsedGroupKeys(groupKeys - normalized)
            }
            updateState { copy(focusGroupKey = normalized) }
            return
        }

        updateState { copy(focusGroupKey = normalized) }
        PLog.w(TAG, "focusGroupFromDrawer directoryGroups empty, falling back to loadAllInternal for group=$normalized")
        loadAllInternal()
    }

    fun consumeFocusGroup() {
        updateState { copy(focusGroupKey = null) }
    }

    fun setSelectedItem(itemId: Long?) {
        updateState { copy(selectedItemId = itemId, pendingSelectedItemScrollId = itemId) }
    }

    fun consumePendingSelectedItemScroll() {
        updateState { copy(pendingSelectedItemScrollId = null) }
    }

    fun sourceFileNameForItemId(itemId: Long): String? {
        return currentState.sourceFileNames[itemId]
    }

    fun prefetchSourceFileNames(items: List<KnowledgeItem>) {
        val unresolvedIds = items
            .map { it.id }
            .filter { it > 0L && !currentState.sourceFileNames.containsKey(it) }
            .distinct()
        if (unresolvedIds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val resolved = buildMap<Long, String> {
                for (itemId in unresolvedIds) {
                    val fileName = useCase.resolveFileNameForItemId(itemId)
                    if (!fileName.isNullOrBlank()) put(itemId, fileName)
                }
            }
            if (resolved.isEmpty()) return@launch
            updateState { copy(sourceFileNames = currentState.sourceFileNames + resolved) }
        }
    }

    fun setCollapsedGroupKeys(keys: Set<String>) {
        if (currentState.collapsedGroupKeys == keys) return
        updateState { copy(collapsedGroupKeys = keys) }
        savedStateHandle[KEY_COLLAPSED_GROUP_KEYS] = ArrayList(keys)
    }

    fun toggleCollapsedGroupKey(key: String) {
        val updated = if (currentState.collapsedGroupKeys.contains(key)) {
            currentState.collapsedGroupKeys - key
        } else {
            currentState.collapsedGroupKeys + key
        }
        setCollapsedGroupKeys(updated)
    }

    private fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val deduped = currentState.searchHistory.filter { it.query != trimmed }
        val newList = listOf(com.example.powerai.domain.model.SearchEntry(trimmed, now)) + deduped
        updateState { copy(searchHistory = if (newList.size > 50) newList.take(50) else newList) }
        viewModelScope.launch(Dispatchers.IO) {
            useCase.addSearchHistory(trimmed)
        }
    }

    fun clearSearchHistory() {
        updateState { copy(searchHistory = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            useCase.clearSearchHistory()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importManager.refreshImportDiagnostics()
            } catch (_: Throwable) {}
            try {
                val loaded = useCase.loadHistory()
                if (loaded.isNotEmpty()) updateState { copy(searchHistory = loaded) }
            } catch (_: Throwable) {}
        }

        viewModelScope.launch(Dispatchers.IO) {
            importDiagnostics.collectLatest { diagnostics ->
                val completionSignature = if (
                    diagnostics.scannedCount > 0 &&
                    diagnostics.importedCount + diagnostics.failedCount >= diagnostics.scannedCount
                ) {
                    "${diagnostics.scannedCount}:${diagnostics.importedCount}:${diagnostics.failedCount}:${diagnostics.lastScanAt}"
                } else {
                    null
                }

                if (completionSignature == null || completionSignature == lastCompletedImportSignature) {
                    return@collectLatest
                }

                lastCompletedImportSignature = completionSignature
                if (!hasLoadedContent) {
                    PLog.d(TAG, "importDiagnostics completed but database tab not loaded yet; skip auto reload")
                    return@collectLatest
                }

                PLog.d(
                    TAG,
                    "importDiagnostics completed scanned=${diagnostics.scannedCount} imported=${diagnostics.importedCount} failed=${diagnostics.failedCount}; auto reload currentQuery=${currentState.currentQuery}"
                )
                reloadCurrentContent()
            }
        }
    }
}

