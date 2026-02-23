package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.retrieval.RetrievalFusionService
import javax.inject.Inject

/**
 * Use case wrapper around RetrievalFusionService exposing a simple list of KnowledgeItem
 * for existing callers (keeps minimal migration surface).
 */
class RetrievalFusionUseCase @Inject constructor(
    private val fusionService: RetrievalFusionService
) {
    suspend fun invoke(keyword: String, limit: Int = 10, forceAnn: Boolean = false): List<KnowledgeItem> {
        return fusionService.retrieve(keyword, limit, forceAnn).map { it.item }
    }
}
