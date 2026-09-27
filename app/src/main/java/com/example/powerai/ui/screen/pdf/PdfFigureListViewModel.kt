package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.PdfFigureItem
import com.example.powerai.domain.usecase.PdfFigureListUseCase
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Observable state of the PDF figures/tables list. */
data class PdfFigureListState(
    val isLoading: Boolean = false,
    val figures: List<PdfFigureItem> = emptyList(),
    val loadedFileId: String? = null,
)

/** User intents for the figures/tables list. */
sealed interface PdfFigureListIntent {
    /** Load (or re-request) the figure list for a document. */
    data class Load(val fileId: String) : PdfFigureListIntent
}

/**
 * One-shot effects of the figures/tables list. The screen is fully
 * state-driven today, so intentionally no effect events exist yet.
 */
sealed interface PdfFigureListEffect

@HiltViewModel
class PdfFigureListViewModel
    @Inject
    constructor(
        private val useCase: PdfFigureListUseCase,
        @javax.inject.Named("io")
        private val ioDispatcher: CoroutineDispatcher,
    ) : BaseMviViewModel<PdfFigureListIntent, PdfFigureListState, PdfFigureListEffect>(
            initialState = PdfFigureListState(),
        ) {
        private var loadJob: Job? = null

        override fun onIntent(intent: PdfFigureListIntent) {
            when (intent) {
                is PdfFigureListIntent.Load -> loadFor(intent.fileId)
            }
        }

        private fun loadFor(fileId: String) {
            if (fileId.isBlank()) return
            if (currentState.loadedFileId == fileId) return
            updateState { copy(loadedFileId = fileId) }
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch(ioDispatcher) {
                    updateState { copy(isLoading = true) }
                    val items = useCase.loadFigures(fileId)
                    updateState { copy(figures = items) }
                    updateState { copy(isLoading = false) }
                }
        }
    }
