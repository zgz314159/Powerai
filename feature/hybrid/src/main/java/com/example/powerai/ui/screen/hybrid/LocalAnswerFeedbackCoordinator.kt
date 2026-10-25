package com.example.powerai.ui.screen.hybrid

import com.example.powerai.data.settings.LocalAnswerFeedbackStore
import com.example.powerai.domain.model.LocalAnswerFeedback

/**
 * Local answer feedback for [HybridViewModel]: positive/negative toggles,
 * duplicate (re-toggle) handling, blank-query guard, persistence through the
 * existing [LocalAnswerFeedbackStore] and submission-time sync.
 */
internal class LocalAnswerFeedbackCoordinator(
    private val store: LocalAnswerFeedbackStore,
    private val stateAccess: HybridStateAccess,
) {
    fun toggleUp() {
        val next =
            if (stateAccess.state.localAnswerFeedback == LocalAnswerFeedback.UP) {
                LocalAnswerFeedback.NONE
            } else {
                LocalAnswerFeedback.UP
            }
        set(next)
    }

    fun toggleDown() {
        val next =
            if (stateAccess.state.localAnswerFeedback == LocalAnswerFeedback.DOWN) {
                LocalAnswerFeedback.NONE
            } else {
                LocalAnswerFeedback.DOWN
            }
        set(next)
    }

    fun set(feedback: LocalAnswerFeedback) {
        val query = stateAccess.state.localSummaryState.query
        if (query.isBlank()) return
        store.setFeedback(query, feedback)
        stateAccess.reduce { copy(localAnswerFeedback = feedback) }
    }

    fun sync(query: String) {
        val feedback = store.getFeedback(query)
        stateAccess.reduce { copy(localAnswerFeedback = feedback) }
    }
}
