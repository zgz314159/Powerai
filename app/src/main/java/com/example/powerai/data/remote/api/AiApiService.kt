package com.example.powerai.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI Chat Completions API（OpenAI/DeepSeek 兼容）。
 */

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatChoice(
    val index: Int? = null,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    val finish_reason: String? = null
)

data class ChatCompletionsResponse(
    val id: String? = null,
    val choices: List<ChatChoice>? = null
)

interface AiApiService {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatCompletionsRequest): ChatCompletionsResponse
}
