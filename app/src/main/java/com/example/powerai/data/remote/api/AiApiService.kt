package com.example.powerai.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI API 接口（如 DeepSeek），用于向 AI 发送问题并接收回答。
 */
data class AiRequest(
    val question: String,
    val reference: String
)

interface AiApiService {
    @POST("/ask")
    suspend fun askAi(@Body request: AiRequest): String
}
