package com.example.powerai.ui.screen.database

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseFocusTarget
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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
    val importProgress: com.example.powerai.core.data.importer.ImportProgress? = null,
    val importDiagnostics: com.example.powerai.data.importer.AssetImportDiagnostics? = null,
)

@HiltViewModel
class DatabaseViewModel
    @Inject
    constructor(
        private val useCase: com.example.powerai.domain.usecase.DatabaseUseCase,
        private val importManager: com.example.powerai.data.importer.DocumentImportManager,
        private val savedStateHandle: SavedStateHandle,
        @javax.inject.Named("io")
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : BaseMviViewModel<DatabaseIntent, DatabaseUiState, Nothing>(
            initialState =
                DatabaseUiState(
                    currentQuery = savedStateHandle[KEY_CURRENT_QUERY] ?: "",
                    collapsedGroupKeys = savedStateHandle.get<ArrayList<String>>(KEY_COLLAPSED_GROUP_KEYS)?.toSet().orEmpty(),
                ),
        ) {
        private companion object {
            const val KEY_CURRENT_QUERY = "database_current_query"
            const val KEY_COLLAPSED_GROUP_KEYS = "database_collapsed_group_keys"
        }

        private val loadCoordinator =
            DatabaseLoadCoordinator(
                useCase = useCase,
                importManager = importManager,
                ioDispatcher = ioDispatcher,
                state = { currentState },
                reduce = { reducer -> updateState(reducer) },
            )

        private val historyStore =
            DatabaseSearchHistoryStore(
                useCase = useCase,
                ioDispatcher = ioDispatcher,
                state = { currentState },
                reduce = { reducer -> updateState(reducer) },
            )

        private val focusCoordinator =
            DatabaseFocusCoordinator(
                loadCoordinator = loadCoordinator,
                state = { currentState },
                reduce = { reducer -> updateState(reducer) },
                clearQuery = { updateCurrentQuery("") },
                setCollapsedGroupKeys = { keys -> setCollapsedGroupKeys(keys) },
            )

        val directoryGroups: StateFlow<List<DatabaseFileGroup>>
            get() = loadCoordinator.directoryGroups

        val importProgress: StateFlow<com.example.powerai.core.data.importer.ImportProgress?> = importManager.progress
        val importDiagnostics: StateFlow<com.example.powerai.data.importer.AssetImportDiagnostics> = importManager.importDiagnostics

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

        fun refresh() {
            reloadCurrentContent()
        }

        fun refreshImportDiagnostics() {
            viewModelScope.launch(ioDispatcher) {
                importManager.refreshImportDiagnostics()
            }
        }

        fun loadAll() {
            updateCurrentQuery("")
            loadCoordinator.loadAll(viewModelScope)
        }

        fun ensureLoaded() {
            loadCoordinator.ensureLoaded(viewModelScope)
        }

        fun reloadCurrentContent() {
            loadCoordinator.reload(viewModelScope)
        }

        fun search(rawQuery: String) {
            val query = rawQuery.trim()
            updateCurrentQuery(query)
            if (query.isBlank()) {
                loadCoordinator.loadAll(viewModelScope)
                return
            }
            historyStore.add(viewModelScope, query)
            loadCoordinator.search(viewModelScope, query)
        }

        private fun updateCurrentQuery(query: String) {
            updateState { copy(currentQuery = query) }
            savedStateHandle[KEY_CURRENT_QUERY] = query
        }

        fun focusItem(itemId: Long) {
            focusCoordinator.focusItem(viewModelScope, itemId)
        }

        fun focusItem(item: KnowledgeItem) {
            focusCoordinator.focusItem(viewModelScope, item)
        }

        fun consumeFocusTarget() {
            updateState { copy(focusTarget = null) }
        }

        fun focusGroup(groupKey: String) {
            focusCoordinator.focusGroup(groupKey)
        }

        fun focusGroupFromDrawer(groupKey: String) {
            focusCoordinator.focusGroupFromDrawer(viewModelScope, groupKey)
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
            loadCoordinator.prefetchSourceFileNames(viewModelScope, items)
        }

        fun setCollapsedGroupKeys(keys: Set<String>) {
            if (currentState.collapsedGroupKeys == keys) return
            updateState { copy(collapsedGroupKeys = keys) }
            savedStateHandle[KEY_COLLAPSED_GROUP_KEYS] = ArrayList(keys)
        }

        fun toggleCollapsedGroupKey(key: String) {
            val updated =
                if (currentState.collapsedGroupKeys.contains(key)) {
                    currentState.collapsedGroupKeys - key
                } else {
                    currentState.collapsedGroupKeys + key
                }
            setCollapsedGroupKeys(updated)
        }

        fun clearSearchHistory() {
            historyStore.clear(viewModelScope)
        }

        init {
            viewModelScope.launch(ioDispatcher) {
                try {
                    importManager.refreshImportDiagnostics()
                } catch (_: Throwable) {
                }
                historyStore.initialize()
            }
            loadCoordinator.observeImportDiagnostics(viewModelScope)
        }
    }
