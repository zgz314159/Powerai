package com.example.powerai.ui.screen.hybrid

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.usecase.AskAiUseCase
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.domain.usecase.RetrievalFusionUseCase
import com.example.powerai.ui.screen.main.DisplayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI 状态数据类 */
data class HybridUiState(
    val question: String = "",
    val answer: String = "",
    val references: List<KnowledgeItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HybridViewModel @Inject constructor(
    private val useCase: HybridQueryUseCase,
    private val localSearchUseCase: RetrievalFusionUseCase,
    private val askAiUseCase: AskAiUseCase,
    private val importer: com.example.powerai.data.importer.DocumentImportManager,
    private val localHistoryStore: com.example.powerai.data.local.LocalSearchHistoryStore,
    private val smartHistoryStore: com.example.powerai.data.local.SmartSearchHistoryStore
) : ViewModel() {
    private val tag = "HybridVM"

    /**
     * `HybridUiState` extended with optional `importProgress` so UI can
     * observe import progress emitted by `DocumentImportManager.progress`.
     */
    data class UiStateWithImport(
        val question: String = "",
        val answer: String = "",
        val references: List<KnowledgeItem> = emptyList(),
        val isLoading: Boolean = false,
        val askedAtMillis: Long? = null,
        val importProgress: com.example.powerai.data.importer.ImportProgress? = null,
        val webSearchEnabled: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiStateWithImport())
    val uiState: StateFlow<UiStateWithImport> = _uiState

    // Simple in-memory local search history (most recent first).
    private val _localSearchHistory = MutableStateFlow<List<com.example.powerai.data.local.LocalSearchEntry>>(emptyList())
    val localSearchHistory: StateFlow<List<com.example.powerai.data.local.LocalSearchEntry>> = _localSearchHistory

    private val _smartSearchHistory = MutableStateFlow<List<com.example.powerai.data.local.LocalSearchEntry>>(emptyList())
    val smartSearchHistory: StateFlow<List<com.example.powerai.data.local.LocalSearchEntry>> = _smartSearchHistory

    // persisted store is injected

    private fun addLocalSearchQuery(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        // remove existing duplicates by query
        val deduped = _localSearchHistory.value.filter { it.query != trimmed }
        val newList = listOf(com.example.powerai.data.local.LocalSearchEntry(trimmed, now)) + deduped
        // keep at most 50 entries
        _localSearchHistory.value = if (newList.size > 50) newList.take(50) else newList
        // persist asynchronously
        viewModelScope.launch(Dispatchers.IO) {
            try {
                localHistoryStore.save(_localSearchHistory.value)
            } catch (_: Throwable) {
            }
        }
    }

    private fun addSmartSearchQuery(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val deduped = _smartSearchHistory.value.filter { it.query != trimmed }
        val newList = listOf(com.example.powerai.data.local.LocalSearchEntry(trimmed, now)) + deduped
        _smartSearchHistory.value = if (newList.size > 50) newList.take(50) else newList
        viewModelScope.launch(Dispatchers.IO) {
            try { smartHistoryStore.save(_smartSearchHistory.value) } catch (_: Throwable) {}
        }
    }

    fun clearSmartSearchHistory() {
        _smartSearchHistory.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try { smartHistoryStore.clear() } catch (_: Throwable) {}
        }
    }

    // Expose a method to clear the local search history
    fun clearLocalSearchHistory() {
        _localSearchHistory.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                localHistoryStore.clear()
            } catch (_: Throwable) {
            }
        }
    }

    init {
        // Observe importer progress and reflect it in the UI state
        viewModelScope.launch {
            importer.progress.collect { progress ->
                _uiState.value = _uiState.value.copy(importProgress = progress)
            }
        }

        // load persisted local history
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = localHistoryStore.load()
                if (loaded.isNotEmpty()) {
                    _localSearchHistory.value = loaded
                }
            } catch (_: Throwable) {
            }
        }

        // Fallback: ensure the shipped assets KB is imported into Room.
        // WorkManager also enqueues this on app start, but this guards against
        // cases where WorkManager is delayed or disabled in debug/dev runs.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importer.importAssetsIfNeed()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Import a document referenced by `uri` using the injected
     * `DocumentImportManager`. The manager owns the application `Context`
     * and repository wiring so ViewModels remain testable and DI-friendly.
     */
    fun importDocument(uri: android.net.Uri) {
        // Delegate to importer which runs on IO internally
        importer.importUri(uri)
        // Optionally kick off a query after import; keep it lightweight
        if (_uiState.value.question.isNotBlank()) {
            submitQuery(_uiState.value.question)
        }
    }

    fun submitQuery(question: String) {
        submitQuery(question, DisplayMode.SMART)
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        Log.d(tag, "setWebSearchEnabled=$enabled")
        _uiState.value = _uiState.value.copy(webSearchEnabled = enabled)
    }

    fun submitQuery(question: String, mode: DisplayMode) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            question = question,
            askedAtMillis = System.currentTimeMillis()
        )
        Log.d(tag, "submitQuery mode=$mode webSearchEnabled=${_uiState.value.webSearchEnabled} q='${question.take(80)}'")
        viewModelScope.launch {
            when (mode) {
                DisplayMode.LOCAL -> {
                    val local = try {
                        localSearchUseCase.invoke(question)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    // record local search into history for drawer
                    addLocalSearchQuery(question)
                    _uiState.value = _uiState.value.copy(
                        answer = "",
                        references = local,
                        isLoading = false
                    )
                }

                DisplayMode.AI -> {
                    val answer = try {
                        askAiUseCase.invokeAiSearch(question, webSearchEnabled = _uiState.value.webSearchEnabled)
                    } catch (t: Throwable) {
                        "AI error: ${t.message ?: t::class.simpleName}"
                    }
                    _uiState.value = _uiState.value.copy(
                        answer = answer,
                        references = emptyList(),
                        isLoading = false
                    )
                }

                DisplayMode.SMART -> {
                    // Use HybridQueryUseCase which already prefers ANN-backed retrieval
                    val result = try {
                        useCase.invoke(question)
                    } catch (t: Throwable) {
                        com.example.powerai.domain.model.QueryResult(answer = "AI error: ${t.message ?: t::class.simpleName}", references = emptyList(), confidence = 0f)
                    }
                    _uiState.value = _uiState.value.copy(
                        answer = result.answer,
                        references = result.references,
                        isLoading = false
                    )
                    // record smart query
                    addSmartSearchQuery(question)
                }
            }
        }
    }
}
