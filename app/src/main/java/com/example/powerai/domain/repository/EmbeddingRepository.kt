package com.example.powerai.domain.repository

import com.example.powerai.domain.model.KnowledgeItem

/**
 * Abstraction for embedding lifecycle: enqueue items for embedding and store resulting vectors.
 */
interface EmbeddingRepository {
    /**
     * Enqueue items for embedding generation. Implementation may persist placeholders and schedule work.
     */
    suspend fun enqueueForEmbedding(items: List<KnowledgeItem>)

    /**
     * Store computed embedding for an item (item.id should be set).
     */
    suspend fun storeEmbedding(itemId: Long, embedding: FloatArray)
}
