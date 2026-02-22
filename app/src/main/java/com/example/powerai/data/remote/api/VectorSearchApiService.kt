package com.example.powerai.data.remote.api

import com.example.powerai.data.remote.dto.VectorSearchRequest
import com.example.powerai.data.remote.dto.VectorSearchResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface VectorSearchApiService {
    @POST("search")
    suspend fun search(@Body request: VectorSearchRequest): VectorSearchResponse
}
