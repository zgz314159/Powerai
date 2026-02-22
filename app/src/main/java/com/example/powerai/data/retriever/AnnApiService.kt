package com.example.powerai.data.retriever

import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit DTOs and service for a simple vector search HTTP API. */
data class AnnSearchRequest(val query: String, val k: Int = 10)
data class AnnSearchResult(val id: Int, val score: Float)
data class AnnSearchResponse(val results: List<AnnSearchResult>)

interface AnnApiService {
    @POST("search")
    suspend fun search(@Body req: AnnSearchRequest): AnnSearchResponse
}
