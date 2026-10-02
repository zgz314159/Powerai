package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.model.QueryResult

internal object HybridModeFallbackFactory {
    /**
     * Retryable local-search failure. Unlike a genuine zero-hit result this
     * must not be rendered as the "no match" empty state, and it never carries
     * the exception message into the UI state.
     */
    fun localFailure(query: String): LocalModeUiPayload {
        return LocalModeUiPayload(
            references = emptyList(),
            evidence = emptyList(),
            summaryState = LocalSummaryStateFactory.searchFailed(query),
            gemmaJob = null,
        )
    }

    fun aiFailure(t: Throwable): String {
        return "AI error: ${t.message ?: t::class.simpleName}"
    }

    fun smartFailure(t: Throwable): QueryResult {
        return QueryResult(
            answer = aiFailure(t),
            references = emptyList(),
            confidence = 0f
        )
    }
}
