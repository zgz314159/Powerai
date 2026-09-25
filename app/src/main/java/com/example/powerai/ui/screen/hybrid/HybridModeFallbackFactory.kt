package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.model.QueryResult
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.domain.usecase.LocalAnswerPlanner

internal object HybridModeFallbackFactory {

    fun localFailure(rawQuestion: String): HybridQueryUseCase.LocalModeResult {
        return HybridQueryUseCase.LocalModeResult(
            query = HybridQueryUseCase.LocalQueryResult(
                retrievals = emptyList(),
                items = emptyList(),
                totalCandidateCount = 0,
                refinedCandidateCount = 0,
                extractiveAnswer = null
            ),
            assessment = LocalAnswerPlanner.assess(rawQuestion, emptyList()),
            gemmaJob = null
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
