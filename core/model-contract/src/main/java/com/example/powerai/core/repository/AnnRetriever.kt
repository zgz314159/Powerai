package com.example.powerai.core.repository

import com.example.powerai.core.model.RetrievalResult

/**
 * ANN retriever abstraction. Implementations may call a remote vector service or a native index.
 */
interface AnnRetriever {
    /**
     * Search for top-k nearest neighbor results for a given query string.
     */
    suspend fun search(query: String, k: Int = 10): List<RetrievalResult>
}
