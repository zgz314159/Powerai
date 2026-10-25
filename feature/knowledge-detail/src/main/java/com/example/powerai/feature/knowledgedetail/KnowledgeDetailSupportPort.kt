package com.example.powerai.feature.knowledgedetail

import com.example.powerai.domain.model.DetailEntryNavigation

/**
 * Narrow app-side surface for Knowledge Detail (Phase C2).
 * Keeps DatabaseUseCase / importer orchestration out of the feature module.
 */
interface KnowledgeDetailSupportPort {
    suspend fun resolveFileNameForItemId(entityId: Long): String?

    suspend fun resolveDetailEntryNavigation(
        entityId: Long,
        query: String
    ): DetailEntryNavigation?
}
