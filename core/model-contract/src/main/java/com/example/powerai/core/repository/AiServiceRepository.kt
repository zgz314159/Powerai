package com.example.powerai.core.repository

import com.example.powerai.core.model.chat.ChatCompletionsRequest
import com.example.powerai.core.model.chat.ChatCompletionsResponse

/**
 * Interface for AI chat completion services.
 */
interface AiServiceRepository {
    /** Perform a non-streaming chat completion request. */
    suspend fun chatCompletions(request: ChatCompletionsRequest): ChatCompletionsResponse
}
