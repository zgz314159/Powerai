package com.example.powerai.ui.screen.hybrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.usecase.HybridQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val importer: com.example.powerai.data.importer.DocumentImportManager
) : ViewModel() {
    /**
     * `HybridUiState` extended with optional `importProgress` so UI can
     * observe import progress emitted by `DocumentImportManager.progress`.
     */
    data class UiStateWithImport(
        val question: String = "",
        val answer: String = "",
        val references: List<KnowledgeItem> = emptyList(),
        val isLoading: Boolean = false,
        val importProgress: com.example.powerai.data.importer.ImportProgress? = null
    )

    private val _uiState = MutableStateFlow(UiStateWithImport())
    val uiState: StateFlow<UiStateWithImport> = _uiState
    
    init {
        // Observe importer progress and reflect it in the UI state
        viewModelScope.launch {
            importer.progress.collect { progress ->
                _uiState.value = _uiState.value.copy(importProgress = progress)
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
        _uiState.value = _uiState.value.copy(isLoading = true, question = question)
        viewModelScope.launch {
            val result = useCase.invoke(question)
            _uiState.value = _uiState.value.copy(
                answer = result.answer,
                references = result.references,
                isLoading = false
            )
        }
    }
}
