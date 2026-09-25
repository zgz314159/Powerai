package com.example.powerai.engine.ai

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI Chat Completions API (OpenAI/DeepSeek compatible).
 * Lives in engine:ai so this module does not depend on the app layer.
 */
data class ApiChatMessage(
    val role: String,
    val content: String
)

data class ApiChatCompletionsRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean = false
)

data class ApiChatChoice(
    val index: Int? = null,
    val message: ApiChatMessage? = null,
    val delta: ApiChatMessage? = null,
    val finish_reason: String? = null
)

data class ApiChatCompletionsResponse(
    val id: String? = null,
    val choices: List<ApiChatChoice>? = null
)

interface AiApiService {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ApiChatCompletionsRequest): ApiChatCompletionsResponse
}
