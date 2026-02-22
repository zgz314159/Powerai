package com.example.powerai.data.vision

import android.content.Context
import android.util.Base64
import com.example.powerai.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val httpClient = OkHttpClient()

    suspend fun analyzeTableToMarkdownWithGemini(imageUri: String, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("GEMINI_API_KEY 未配置")
        }

        val bytes = readBytesFromUriOrNull(imageUri)
            ?: throw IllegalArgumentException("无法读取图片: $imageUri")

        val modelName = BuildConfig.GEMINI_MODEL.trim().ifBlank { "gemini-1.5-flash" }
        val mimeType = sniffImageMimeType(bytes)
        val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val reqJson = buildGeminiGenerateContentJson(
            prompt = prompt,
            mimeType = mimeType,
            base64Data = base64Data
        )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(reqJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyStr = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Gemini 调用失败: HTTP ${response.code} ${bodyStr.take(500)}")
        }

        val out = extractGeminiTextOrNull(bodyStr).orEmpty()
        stripCodeFences(out).trim()
    }

    private fun stripCodeFences(s: String): String {
        val t = s.trim()
        if (!t.startsWith("```")) return s
        val lines = t.lines()
        if (lines.size < 2) return s
        val start = lines.indexOfFirst { it.trim().startsWith("```") }
        val end = lines.indexOfLast { it.trim().startsWith("```") }
        if (start == -1 || end == -1 || end <= start) return s
        return lines.subList(start + 1, end).joinToString("\n")
    }

    private fun readBytesFromUriOrNull(uriStr: String): ByteArray? {
        return try {
            when {
                uriStr.startsWith("file:///android_asset/") -> {
                    val rel = uriStr.removePrefix("file:///android_asset/").trimStart('/')
                    context.assets.open(rel).use { it.readBytes() }
                }

                uriStr.startsWith("content://") -> {
                    val uri = android.net.Uri.parse(uriStr)
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                uriStr.startsWith("file://") -> {
                    val path = android.net.Uri.parse(uriStr).path ?: return null
                    File(path).takeIf { it.exists() }?.readBytes()
                }

                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun sniffImageMimeType(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        return "application/octet-stream"
    }

    private fun buildGeminiGenerateContentJson(prompt: String, mimeType: String, base64Data: String): JsonObject {
        // API: https://ai.google.dev/api/rest/v1beta/models/generateContent
        val inlineData = JsonObject().apply {
            addProperty("mime_type", mimeType)
            addProperty("data", base64Data)
        }
        val imagePart = JsonObject().apply { add("inline_data", inlineData) }
        val textPart = JsonObject().apply { addProperty("text", prompt) }

        val parts = com.google.gson.JsonArray().apply {
            add(textPart)
            add(imagePart)
        }
        val content = JsonObject().apply {
            addProperty("role", "user")
            add("parts", parts)
        }

        val contents = com.google.gson.JsonArray().apply { add(content) }

        val generationConfig = JsonObject().apply {
            addProperty("temperature", 0.2)
        }

        return JsonObject().apply {
            add("contents", contents)
            add("generationConfig", generationConfig)
        }
    }

    private fun extractGeminiTextOrNull(responseJson: String): String? {
        return try {
            val el = JsonParser().parse(responseJson)
            if (!el.isJsonObject) return null
            val root = el.asJsonObject

            val candidatesEl = root.get("candidates") ?: return null
            if (!candidatesEl.isJsonArray) return null
            val candidates = candidatesEl.asJsonArray
            if (candidates.size() <= 0) return null

            val firstCandidateEl = candidates[0]
            if (!firstCandidateEl.isJsonObject) return null
            val firstCandidate = firstCandidateEl.asJsonObject

            val contentEl = firstCandidate.get("content") ?: return null
            if (!contentEl.isJsonObject) return null
            val contentObj = contentEl.asJsonObject

            val partsEl = contentObj.get("parts") ?: return null
            if (!partsEl.isJsonArray) return null
            val parts = partsEl.asJsonArray

            for (partEl in parts) {
                if (!partEl.isJsonObject) continue
                val obj = partEl.asJsonObject
                val textEl = obj.get("text")
                if (textEl != null && textEl.isJsonPrimitive) {
                    val p = textEl.asJsonPrimitive
                    if (p.isString) {
                        val s = p.asString
                        if (s.isNotBlank()) return s
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
