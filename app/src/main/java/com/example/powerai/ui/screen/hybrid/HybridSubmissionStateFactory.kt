package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.usecase.LocalAnswerPlanner
import com.example.powerai.ui.screen.main.DisplayMode

internal data class HybridSubmissionPreparation(
    val sanitizedQuestion: String,
    val ftsQuery: String,
    val nextUiState: HybridUiState,
    val clearAiAnswer: Boolean,
    val clearEvidence: Boolean,
    val resetLocalUi: Boolean,
    val resetSmartUi: Boolean,
    val localSummaryState: LocalSummaryState?
)

internal object HybridSubmissionStateFactory {

    fun prepare(
        current: HybridUiState,
        question: String,
        mode: DisplayMode,
        askedAtMillis: Long
    ): HybridSubmissionPreparation {
        val sanitized = HybridUtils.sanitizeQuestion(question)
        val ftsQuery = HybridUtils.normalizeForFts(sanitized)
        val nextUiState = HybridUiStateFactory.submissionStarted(
            current = current,
            question = question,
            sanitizedQuestion = sanitized,
            askedAtMillis = askedAtMillis
        )

        return when (mode) {
            DisplayMode.LOCAL -> HybridSubmissionPreparation(
                sanitizedQuestion = sanitized,
                ftsQuery = ftsQuery,
                nextUiState = nextUiState,
                clearAiAnswer = true,
                clearEvidence = true,
                resetLocalUi = true,
                resetSmartUi = false,
                localSummaryState = LocalSummaryStateFactory.initial(
                    query = sanitized,
                    intent = LocalAnswerPlanner.classifyIntent(sanitized)
                )
            )

            DisplayMode.AI -> HybridSubmissionPreparation(
                sanitizedQuestion = sanitized,
                ftsQuery = ftsQuery,
                nextUiState = nextUiState,
                clearAiAnswer = false,
                clearEvidence = false,
                resetLocalUi = false,
                resetSmartUi = false,
                localSummaryState = null
            )

            DisplayMode.SMART -> HybridSubmissionPreparation(
                sanitizedQuestion = sanitized,
                ftsQuery = ftsQuery,
                nextUiState = nextUiState,
                clearAiAnswer = false,
                clearEvidence = false,
                resetLocalUi = false,
                resetSmartUi = true,
                localSummaryState = null
            )
        }
    }
}
