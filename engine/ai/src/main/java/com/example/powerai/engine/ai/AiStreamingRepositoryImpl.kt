package com.example.powerai.engine.ai

import com.example.powerai.core.model.util.Cancellable
import com.example.powerai.core.repository.AiStreamingRepository
import com.example.powerai.core.repository.AiStreamRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiStreamingRepositoryImpl @Inject constructor(
    private val service: AiStreamingService
) : AiStreamingRepository {

    override fun startStreaming(
        request: AiStreamRequest,
        onOpen: () -> Unit,
        onData: (String) -> Unit,
        onClosed: () -> Unit,
        onFailure: (String) -> Unit
    ): Cancellable {
        val okRequest = service.createRequest(
            url = request.url,
            apiKey = request.apiKey,
            bodyJson = request.bodyJson,
            acceptStream = request.acceptStream
        )

        val eventSource = service.startStreaming(
            request = okRequest,
            onOpen = { onOpen() },
            onData = onData,
            onClosed = { onClosed() },
            onFailure = { msg, _ -> onFailure(msg) }
        )

        return object : Cancellable {
            override fun cancel() {
                eventSource.cancel()
            }
        }
    }

    override fun buildBody(model: String, messagesJsonFragments: List<String>, stream: Boolean): String {
        return service.buildBody(model, messagesJsonFragments, stream)
    }

    override fun isDoneMarker(data: String): Boolean {
        return service.isSseDoneMarker(data)
    }

    override fun extractTextChunk(data: String): String? {
        return service.extractTextChunk(data)
    }
}
