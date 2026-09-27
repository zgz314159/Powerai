package com.example.powerai.ui.screen.database

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.usecase.DatabaseUseCase
import com.example.powerai.util.PLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Content loading coordinator for [DatabaseViewModel]: initial load, search,
 * retry and import-triggered reload. A new content request cancels the
 * in-flight one so the newest request always decides the final state. State
 * mutations go through the injected reducer; the ViewModel stays the single
 * owner of [DatabaseUiState].
 */
internal class DatabaseLoadCoordinator(
    private val useCase: DatabaseUseCase,
    private val importManager: DocumentImportManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: () -> DatabaseUiState,
    private val reduce: (DatabaseUiState.() -> DatabaseUiState) -> Unit,
) {
    private companion object {
        private const val TAG = "PowerAiDbDebug"
    }

    private val _directoryGroups = MutableStateFlow<List<DatabaseFileGroup>>(emptyList())
    val directoryGroups: StateFlow<List<DatabaseFileGroup>> = _directoryGroups.asStateFlow()

    var hasLoadedContent: Boolean = false
        private set

    private var contentJob: Job? = null
    private var lastCompletedImportSignature: String? = null

    fun ensureLoaded(scope: CoroutineScope) {
        if (hasLoadedContent) return
        reload(scope)
    }

    fun reload(scope: CoroutineScope) {
        val query = state().currentQuery.trim()
        if (query.isBlank()) {
            loadAll(scope)
        } else {
            search(scope, query)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun loadAll(scope: CoroutineScope) {
        hasLoadedContent = true
        launchContent(scope) {
            PLog.d(
                TAG,
                "loadAllInternal start query=${state().currentQuery} " +
                    "directoryGroups=${_directoryGroups.value.size}",
            )
            reduce { copy(isLoading = true, errorMessage = null) }
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
                reduce { copy(isLoading = false, groups = groups, errorMessage = null) }
                PLog.d(TAG, "loadAllInternal finish uiGroups=${groups.size} error=null")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                PLog.e(TAG, "loadAllInternal failed", t)
                reduce { copy(isLoading = false, errorMessage = t.message ?: "加载失败") }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun search(
        scope: CoroutineScope,
        query: String,
    ) {
        hasLoadedContent = true
        launchContent(scope) {
            PLog.d(TAG, "searchInternal start query=$query directoryGroups=${_directoryGroups.value.size}")
            reduce { copy(isLoading = true, errorMessage = null) }
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
                reduce { copy(isLoading = false, groups = groups, errorMessage = null) }
                PLog.d(TAG, "searchInternal finish uiGroups=${groups.size} query=$query")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                PLog.e(TAG, "searchInternal failed query=$query", t)
                reduce { copy(isLoading = false, errorMessage = t.message ?: "搜索失败") }
            }
        }
    }

    fun prefetchSourceFileNames(
        scope: CoroutineScope,
        items: List<KnowledgeItem>,
    ) {
        val unresolvedIds =
            items
                .map { it.id }
                .filter { it > 0L && !state().sourceFileNames.containsKey(it) }
                .distinct()
        if (unresolvedIds.isEmpty()) return

        scope.launch(ioDispatcher) {
            val resolved =
                buildMap<Long, String> {
                    for (itemId in unresolvedIds) {
                        val fileName = useCase.resolveFileNameForItemId(itemId)
                        if (!fileName.isNullOrBlank()) put(itemId, fileName)
                    }
                }
            if (resolved.isEmpty()) return@launch
            reduce { copy(sourceFileNames = sourceFileNames + resolved) }
        }
    }

    fun observeImportDiagnostics(scope: CoroutineScope): Job =
        scope.launch(ioDispatcher) {
            importManager.importDiagnostics.collectLatest { diagnostics ->
                val completionSignature =
                    if (
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

                val scanned = diagnostics.scannedCount
                val imported = diagnostics.importedCount
                val failed = diagnostics.failedCount
                val query = state().currentQuery
                PLog.d(
                    TAG,
                    "importDiagnostics completed scanned=$scanned imported=$imported failed=$failed; " +
                        "auto reload currentQuery=$query",
                )
                reload(scope)
            }
        }

    private fun launchContent(
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        contentJob?.cancel()
        contentJob = scope.launch(ioDispatcher, block = block)
    }
}
