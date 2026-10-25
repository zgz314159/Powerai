package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.domain.model.QueryResult
import com.example.powerai.core.model.RetrievalResult

internal data class SmartModeUiPayload(
    val answer: String,
    val references: List<KnowledgeItem>,
    val evidence: List<RetrievalResult>
)

internal object SmartModeUiPayloadFactory {
    fun fromResult(result: QueryResult): SmartModeUiPayload {
        return SmartModeUiPayload(
            answer = result.answer,
            references = result.references,
            evidence = result.references.map { item ->
                RetrievalResult(item = item, source = "local", score = 0f)
            }
        )
    }
}
