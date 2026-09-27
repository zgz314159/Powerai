package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.usecase.PdfKnowledgeUseCase
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Observable state of the PDF per-page knowledge overlay. */
data class PdfKnowledgeState(
    val fileId: String = "",
    val page: Int = -1,
    val knowledgeCount: Int = 0,
    val firstItemId: Long? = null,
    val pageItems: List<KnowledgeItem> = emptyList(),
    val pageBlocks: List<Pair<Long, KnowledgeBlock>> = emptyList(),
)

/** User intents for the PDF knowledge overlay. */
sealed interface PdfKnowledgeIntent {
    /** Show the knowledge for one (fileId, page) pair. */
    data class UpdatePage(val fileId: String, val page: Int) : PdfKnowledgeIntent
}

/**
 * One-shot effects of the knowledge overlay. The overlay is fully
 * state-driven today, so intentionally no effect events exist yet.
 */
sealed interface PdfKnowledgeEffect

@HiltViewModel
class PdfKnowledgeViewModel
    @Inject
    constructor(
        private val useCase: PdfKnowledgeUseCase,
        @javax.inject.Named("io")
        private val ioDispatcher: CoroutineDispatcher,
    ) : BaseMviViewModel<PdfKnowledgeIntent, PdfKnowledgeState, PdfKnowledgeEffect>(
            initialState = PdfKnowledgeState(),
        ) {
        private var pageJob: Job? = null

        override fun onIntent(intent: PdfKnowledgeIntent) {
            when (intent) {
                is PdfKnowledgeIntent.UpdatePage -> updatePage(intent.fileId, intent.page)
            }
        }

        private fun updatePage(
            fileId: String,
            page: Int,
        ) {
            if (fileId.isBlank()) return
            if (fileId == currentState.fileId && page == currentState.page) return
            updateState { copy(fileId = fileId, page = page) }
            pageJob?.cancel()
            pageJob =
                viewModelScope.launch(ioDispatcher) {
                    val result = useCase.loadPage(fileId, page)
                    updateState {
                        copy(
                            knowledgeCount = result.knowledgeCount,
                            pageItems = result.pageItems,
                            firstItemId = result.pageItems.firstOrNull()?.id,
                            pageBlocks = result.pageBlocks,
                        )
                    }
                }
        }
    }
