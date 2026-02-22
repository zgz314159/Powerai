package com.example.powerai.ui.screen.hybrid

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object HybridAiStreamingClient {
    private const val TAG = "HybridAiStreaming"

    suspend fun streamAnswer(
        question: String,
        referenceText: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onPartial: (String) -> Unit
    ): String {
        val safeRef = referenceText
            .replace(Regex("\\p{Cntrl}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(8000)

        val userContent = buildString {
            append(question)
            if (safeRef.isNotBlank()) {
                append("\n\n参考：")
                append(safeRef)
            }
        }

        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val client = okhttp3.OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val escaped = userContent
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val bodyJson =
            "{\"model\":\"$model\",\"stream\":true,\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"You are a helpful assistant.\"}," +
                "{\"role\":\"user\",\"content\":\"" + escaped + "\"}] }"

        val req = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(mediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string() ?: "HTTP ${resp.code}"
                Log.d(TAG, "HTTP error msg: \"$msg\" code:${resp.code}")
                throw IllegalStateException(msg)
            }

            val source = resp.body?.source() ?: throw IllegalStateException("Empty response body")

            val sb = StringBuilder()
            while (!source.exhausted()) {
                val line = try {
                    source.readUtf8Line()
                } catch (_: java.io.IOException) {
                    break
                } ?: break

                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val payload = if (trimmed.startsWith("data:")) trimmed.removePrefix("data:").trim() else trimmed
                if (payload == "[DONE]" || payload == "[done]") break

                appendPayloadText(payload, sb)
                onPartial(sb.toString())
            }

            return sb.toString()
        }
    }

    private fun appendPayloadText(payload: String, sb: StringBuilder) {
        try {
            val parsed = try {
                com.google.gson.Gson().fromJson(payload, com.google.gson.JsonElement::class.java)
            } catch (_: Throwable) {
                null
            }

            if (parsed != null && parsed.isJsonObject) {
                val obj = parsed.asJsonObject
                if (obj.has("choices") && obj.get("choices").isJsonArray) {
                    val choices = obj.getAsJsonArray("choices")
                    for (c in choices) {
                        if (!c.isJsonObject) continue
                        val co = c.asJsonObject
                        if (co.has("delta") && co.get("delta").isJsonObject) {
                            val d = co.getAsJsonObject("delta")
                            if (d.has("content")) sb.append(d.get("content").asString)
                        }
                        if (co.has("message") && co.get("message").isJsonObject) {
                            val m = co.getAsJsonObject("message")
                            if (m.has("content")) sb.append(m.get("content").asString)
                        }
                    }
                    return
                }

                listOf("text", "content", "answer", "result").forEach { k ->
                    if (obj.has(k) && obj.get(k).isJsonPrimitive) sb.append(obj.get(k).asString)
                }
                return
            }
        } catch (_: Throwable) {
            // Fall back to raw payload
        }

        sb.append(payload)
    }
}
