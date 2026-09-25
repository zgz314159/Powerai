package com.example.powerai.core.repository

import com.example.powerai.core.model.util.Cancellable

/**
 * Domain model for a streaming request.
 */
data class AiStreamRequest(
    val url: String,
    val apiKey: String,
    val bodyJson: String,
    val acceptStream: Boolean = true
)

/**
 * Interface for AI streaming services.
 */
interface AiStreamingRepository {
    /**
     * Start a streaming AI call and returns a cancellable object.
     */
    fun startStreaming(
        request: AiStreamRequest,
        onOpen: () -> Unit = {},
        onData: (String) -> Unit,
        onClosed: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ): Cancellable

    /** Build the request body for streaming. */
    fun buildBody(model: String, messagesJsonFragments: List<String>, stream: Boolean = true): String

    /** Check if the data chunk is a done marker. */
    fun isDoneMarker(data: String): Boolean

    /** Extract text chunk from raw data. */
    fun extractTextChunk(data: String): String?
}
