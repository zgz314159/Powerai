package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem

internal object HybridUiStateFactory {
    fun submissionStarted(
        current: HybridUiState,
        question: String,
        sanitizedQuestion: String,
        askedAtMillis: Long
    ): HybridUiState {
        return current.copy(
            isLoading = true,
            question = question,
            sanitizedQuestion = sanitizedQuestion,
            askedAtMillis = askedAtMillis
        )
    }

    fun cleared(current: HybridUiState): HybridUiState {
        return current.copy(
            question = "",
            sanitizedQuestion = "",
            answer = "",
            references = emptyList(),
            isLoading = false,
            askedAtMillis = null,
            aiAnswer = null,
            evidenceList = emptyList(),
            localSummaryState = LocalSummaryState()
        )
    }

    fun localCompleted(
        current: HybridUiState,
        references: List<KnowledgeItem>
    ): HybridUiState {
        return current.copy(
            answer = "",
            references = references,
            isLoading = false
        )
    }

    fun aiCompleted(
        current: HybridUiState,
        answer: String
    ): HybridUiState {
        return current.copy(
            answer = answer,
            references = emptyList(),
            isLoading = false
        )
    }

    fun smartCompleted(
        current: HybridUiState,
        answer: String,
        references: List<KnowledgeItem>
    ): HybridUiState {
        return current.copy(
            answer = answer,
            references = references,
            isLoading = false
        )
    }
}
