package com.example.powerai.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interface for AI model interaction, abstracting away network implementation (OkHttp/SSE).
 */
interface RemoteAiRepository {

    /**
     * Start a streaming AI generation request.
     * @param model Model identifier.
     * @param prompt The prompt to send.
     * @param systemPrompt Optional system prompt.
     * @return Flow of text chunks.
     */
    fun streamGenerate(
        model: String,
        prompt: String,
        systemPrompt: String? = null
    ): Flow<RemoteAiResponse>

    sealed class RemoteAiResponse {
        data class Chunk(val text: String) : RemoteAiResponse()
        object Done : RemoteAiResponse()
        data class Error(val message: String, val cause: Throwable? = null) : RemoteAiResponse()
    }
}
