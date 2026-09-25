package com.example.powerai.core.repository

import com.example.powerai.core.model.KnowledgeItem

/**
 * Abstraction for embedding lifecycle.
 */
interface EmbeddingRepository {
    /**
     * Enqueue items for embedding generation.
     */
    suspend fun enqueueForEmbedding(items: List<KnowledgeItem>)

    /**
     * Store computed embedding for an item.
     */
    suspend fun storeEmbedding(itemId: Long, embedding: FloatArray)
}
