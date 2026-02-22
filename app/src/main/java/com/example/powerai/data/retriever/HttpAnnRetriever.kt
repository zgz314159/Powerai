package com.example.powerai.data.retriever

import com.example.powerai.domain.retriever.AnnRetriever
import javax.inject.Inject

/**
 * Simple HTTP-based ANN retriever. Delegates to a Retrofit service which should return result indices.
 */
class HttpAnnRetriever @Inject constructor(
    private val api: AnnApiService
) : AnnRetriever {
    override suspend fun search(query: String, k: Int): List<Int> {
        val resp = api.search(AnnSearchRequest(query, k))
        return resp.results.map { it.id }
    }
}
