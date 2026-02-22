package com.example.powerai.domain.ai

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AiStreamingService @Inject constructor(private val observability: com.example.powerai.util.ObservabilityService) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun buildBody(model: String, messagesJsonFragments: List<String>, stream: Boolean = true): String {
        return buildString {
            append('{')
            append("\"model\":\"")
            append(escapeJson(model))
            append("\",")
            append("\"stream\":")
            append(if (stream) "true" else "false")
            append(",\"messages\":[")
            append(messagesJsonFragments.joinToString(","))
            append(']')
            append('}')
        }
    }

    fun createRequest(url: String, apiKey: String, bodyJson: String, acceptStream: Boolean = true, debugOverrideUrl: String? = null, debugEnabled: Boolean = false, preferGetOverride: Boolean = false): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        return when {
            debugEnabled && !debugOverrideUrl.isNullOrBlank() && preferGetOverride -> {
                Request.Builder()
                    .url(debugOverrideUrl)
                    .addHeader("Accept", "text/event-stream")
                    .get()
                    .build()
            }
            acceptStream -> {
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(bodyJson.toRequestBody(mediaType))
                    .build()
            }
            else -> {
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody(mediaType))
                    .build()
            }
        }
    }

    fun startEventSource(request: Request, listener: EventSourceListener): EventSource {
        return EventSources.createFactory(client).newEventSource(request, listener)
    }

    /**
     * Start a streaming EventSource and deliver raw SSE data chunks via callbacks.
     * Callbacks are invoked on the EventSource thread; callers should switch to Main when updating UI state.
     */
    fun startStreaming(
        request: Request,
        onOpen: (okhttp3.Response) -> Unit = {},
        onData: (String) -> Unit,
        onClosed: () -> Unit = {},
        onFailure: (String, Throwable?) -> Unit = { _, _ -> }
    ): EventSource {
        observability.aiCallStarted(request.url.host, "streaming")
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                try { onOpen(response) } catch (_: Throwable) {}
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try { onData(data) } catch (_: Throwable) {}
            }

            override fun onClosed(eventSource: EventSource) {
                try { onClosed() } catch (_: Throwable) {}
                observability.aiCallFinished(request.url.host, 0L, true, "stream closed")
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val msg = t?.message ?: response?.message ?: "Stream failed"
                try { onFailure(msg, t) } catch (_: Throwable) {}
                observability.aiCallFinished(request.url.host, 0L, false, msg)
            }
        }

        return startEventSource(request, listener)
    }

    fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun extractTextChunk(jsonLike: String): String? {
        val trimmed = jsonLike.trim()
        try {
            val rootElem = JsonParser().parse(trimmed)
            if (rootElem.isJsonObject) {
                val root = rootElem.asJsonObject
                if (root.has("choices") && root.get("choices").isJsonArray) {
                    val arr = root.getAsJsonArray("choices")
                    for (el in arr) {
                        try {
                            val choice = el.asJsonObject
                            if (choice.has("delta") && choice.get("delta").isJsonObject) {
                                val delta = choice.getAsJsonObject("delta")
                                if (delta.has("content")) return unescapeJson(delta.get("content").asString)
                            }
                            if (choice.has("message") && choice.get("message").isJsonObject) {
                                val msg = choice.getAsJsonObject("message")
                                if (msg.has("content")) return unescapeJson(msg.get("content").asString)
                            }
                            if (choice.has("text")) return unescapeJson(choice.get("text").asString)
                        } catch (_: Throwable) {}
                    }
                }
                if (root.has("content")) return unescapeJson(root.get("content").asString)
                if (root.has("text")) return unescapeJson(root.get("text").asString)
            }
        } catch (_: Throwable) {
        }

        val contentRegex = "\\\"content\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"".toRegex()
        val m = contentRegex.find(jsonLike)
        if (m != null) return unescapeJson(m.groupValues[1])
        val textRegex = "\\\"text\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"".toRegex()
        val m2 = textRegex.find(jsonLike)
        if (m2 != null) return unescapeJson(m2.groupValues[1])
        return null
    }

    fun isSseDoneMarker(data: String): Boolean {
        if (data.contains("[DONE]", ignoreCase = true)) return true
        val trimmed = data.trim()
        if (trimmed.isBlank()) return false
        return try {
            val root = JsonParser().parse(trimmed)
            if (!root.isJsonObject) return false
            val obj = root.asJsonObject
            if (!obj.has("choices") || !obj.get("choices").isJsonArray) return false
            obj.getAsJsonArray("choices").any { choiceElem ->
                val choiceObj = try { choiceElem.asJsonObject } catch (_: Throwable) { return@any false }
                if (!choiceObj.has("finish_reason")) return@any false
                val finish = choiceObj.get("finish_reason")
                !finish.isJsonNull && finish.toString().trim('"').isNotBlank()
            }
        } catch (_: Throwable) { false }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
