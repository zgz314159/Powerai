package com.example.powerai.ui.screen.database

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.core.model.ImportedFile
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.LocalHighlightTarget
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.data.importer.AssetImportDiagnostics
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.importer.KbRebuildState
import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import com.example.powerai.domain.usecase.DatabaseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.mockito.Mockito

/**
 * Deterministic collaborators for [DatabaseViewModel] orchestration tests.
 * No real database, network or file IO is involved.
 */
internal class FakeKnowledgeRepository : KnowledgeRepository {
    var importedFiles: List<ImportedFile> = emptyList()
    var allItems: List<KnowledgeItem> = emptyList()
    var searchItems: (String) -> List<KnowledgeItem> = { emptyList() }
    var itemsById: Map<Long, KnowledgeItem> = emptyMap()
    var loadError: Throwable? = null
    var searchError: Throwable? = null
    var loadGate: CompletableDeferred<Unit>? = null
    var searchGate: CompletableDeferred<Unit>? = null
    var loadCount: Int = 0
        private set
    var searchCount: Int = 0
        private set
    var loadWasCancelled: Boolean = false
        private set
    var searchWasCancelled: Boolean = false
        private set

    override suspend fun getAll(): List<KnowledgeItem> {
        loadCount += 1
        loadGate?.let { gate ->
            try {
                gate.await()
            } catch (error: CancellationException) {
                loadWasCancelled = true
                throw error
            }
        }
        loadError?.let { throw it }
        return allItems
    }

    override suspend fun searchByKeyword(query: String): List<KnowledgeItem> {
        searchCount += 1
        searchGate?.let { gate ->
            try {
                gate.await()
            } catch (error: CancellationException) {
                searchWasCancelled = true
                throw error
            }
        }
        searchError?.let { throw it }
        return searchItems(query)
    }

    override suspend fun getImportedFiles(): List<ImportedFile> = importedFiles

    override suspend fun getLocalItemById(id: Long): KnowledgeItem? = itemsById[id]

    override suspend fun importDocuments(uris: List<String>): Result<Unit> = Result.success(Unit)

    override suspend fun insertBatch(items: List<KnowledgeItem>) = Unit

    override suspend fun isFileImported(fileId: String): Boolean = false

    override suspend fun markFileImported(
        fileId: String,
        fileName: String,
        timestamp: Long,
        status: String,
    ) = Unit

    override suspend fun resolveHighlightTarget(
        itemId: Long,
        highlight: String,
    ): LocalHighlightTarget? = null

    override suspend fun resolveTableLabelTargets(
        items: List<KnowledgeItem>,
        matchedText: String?,
    ): List<KnowledgeItem> = items

    override suspend fun countKnowledgeByPage(
        fileId: String,
        page: Int,
    ): Int = 0

    override suspend fun getItemsByPage(
        fileId: String,
        page: Int,
    ): List<KnowledgeItem> = emptyList()
}

internal class FakeSearchHistoryRepository : SearchHistoryRepository {
    val store: MutableMap<HistoryScope, MutableList<SearchEntry>> = mutableMapOf()

    fun seed(entries: List<SearchEntry>) {
        store[HistoryScope.DATABASE] = entries.toMutableList()
    }

    fun storedQueries(): List<String> = store[HistoryScope.DATABASE].orEmpty().map { it.query }

    override suspend fun loadHistory(scope: HistoryScope): List<SearchEntry> = store[scope]?.toList() ?: emptyList()

    override suspend fun saveHistory(
        scope: HistoryScope,
        history: List<SearchEntry>,
    ) {
        store[scope] = history.toMutableList()
    }

    override suspend fun clearHistory(scope: HistoryScope) {
        store.remove(scope)
    }
}

internal fun knowledgeItem(
    id: Long,
    title: String,
    source: String = "班组手册A.docx",
    content: String = "内容$id",
    pageNumber: Int? = null,
    contextLabel: String? = null,
): KnowledgeItem =
    KnowledgeItem(
        id = id,
        title = title,
        content = content,
        source = source,
        pageNumber = pageNumber,
        category = "安规",
        keywords = emptyList(),
        contextLabel = contextLabel,
    )

private const val PDF_SOURCE_FILE_ID =
    "pdf:" + "0123456789abcdef0123456789abcdef" +
        "0123456789abcdef0123456789abcdef::"

internal fun pdfSource(fileName: String): String = PDF_SOURCE_FILE_ID + fileName

/**
 * Owns one [DatabaseViewModel] factory plus its collaborators so tests stay
 * short; [cancelCreatedViewModels] mirrors the HybridViewModel test hygiene.
 */
internal class DatabaseViewModelTestHarness(
    val repo: FakeKnowledgeRepository = FakeKnowledgeRepository(),
    val historyRepo: FakeSearchHistoryRepository = FakeSearchHistoryRepository(),
    val diagnosticsFlow: MutableStateFlow<AssetImportDiagnostics> =
        MutableStateFlow(AssetImportDiagnostics()),
) {
    val rebuildStateFlow: MutableStateFlow<KbRebuildState> = MutableStateFlow(KbRebuildState.Idle)

    val importer: DocumentImportManager =
        Mockito.mock(DocumentImportManager::class.java).also { manager ->
            Mockito.`when`(manager.importDiagnostics).thenReturn(diagnosticsFlow)
            Mockito.`when`(manager.progress).thenReturn(MutableStateFlow<ImportProgress?>(null))
            Mockito.`when`(manager.rebuildState).thenReturn(rebuildStateFlow)
        }

    private val createdViewModels = mutableListOf<DatabaseViewModel>()

    fun createViewModel(
        scope: TestScope,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): DatabaseViewModel {
        val useCase = DatabaseUseCase(repo, historyRepo)
        val viewModel =
            DatabaseViewModel(
                useCase,
                importer,
                savedStateHandle,
                StandardTestDispatcher(scope.testScheduler),
            )
        createdViewModels += viewModel
        return viewModel
    }

    fun cancelCreatedViewModels() {
        createdViewModels.forEach { viewModel ->
            try {
                viewModel.viewModelScope.coroutineContext.job.cancel()
            } catch (_: Throwable) {
            }
        }
        createdViewModels.clear()
    }
}
