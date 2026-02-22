package com.example.powerai.domain.retriever

/**
 * ANN retriever abstraction. Implementations may call a remote vector service or a native index.
 */
interface AnnRetriever {
    /**
     * Search for top-k nearest neighbor indices for a given query string.
     * Returns a list of integer indices corresponding to metadata positions in the embedding store.
     */
    suspend fun search(query: String, k: Int = 10): List<Int>
}
