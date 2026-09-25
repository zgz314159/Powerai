package com.example.powerai.data.retriever

import com.example.powerai.core.repository.AnnRetriever
import com.example.powerai.core.model.RetrievalResult
import javax.inject.Inject

/**
 * Simple HTTP-based ANN retriever. Delegates to a Retrofit service which should return result indices.
 */
class HttpAnnRetriever @Inject constructor(
    private val api: AnnApiService
) : AnnRetriever {
    override suspend fun search(query: String, k: Int): List<RetrievalResult> {
        val resp = api.search(AnnSearchRequest(query, k))
        return resp.results.map { hit ->
            RetrievalResult(
                id = hit.id.toLong(),
                score = 0f,
                confidence = null,
                source = "remote_http",
                metadata = emptyMap(),
                vectorPresent = false,
                item = null,
                debug = emptyMap()
            )
        }
    }
}
