package com.example.powerai.data.vision

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.powerai.BuildConfig
import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.remote.api.ChatCompletionsRequest
import com.example.powerai.data.remote.api.ChatMessage
import com.example.powerai.domain.vision.VisionBoostRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionBoostRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val aiApiService: AiApiService,
    private val visionService: VisionService
) : VisionBoostRepository {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun analyzeTableToMarkdown(imageUri: String, enableDeepLogicValidation: Boolean): String = withContext(Dispatchers.IO) {
        val prompt = buildPromptForTable()

        val markdown = runCatching {
            // Prefer Gemini when configured (blocksJson already maps the correct snapshot).
            if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                visionService.analyzeTableToMarkdownWithGemini(imageUri = imageUri, prompt = prompt)
            } else {
                throw IllegalStateException("GEMINI_API_KEY not set")
            }
        }.getOrElse {
            // Fallback to existing OpenAI-style multimodal gateway.
            analyzeWithGateway(imageUri = imageUri, prompt = prompt)
        }

        if (!enableDeepLogicValidation) {
            return@withContext markdown
        }

        val report = deepSeekValidateMarkdown(markdown)
        if (report.isBlank()) {
            return@withContext markdown
        }

        return@withContext (markdown.trimEnd() + "\n\n---\n\n## 深度逻辑验证（DeepSeek）\n\n" + report.trim()).trim()
    }

    private fun analyzeWithGateway(imageUri: String, prompt: String): String {
        val bytes = readBytesFromUriOrNull(imageUri)
            ?: throw IllegalArgumentException("无法读取图片: $imageUri")

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:image/png;base64,$b64"

        val url = buildVisionUrl()
        val reqJson = buildRequestJson(prompt = prompt, imageDataUrl = dataUrl)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .apply {
                val apiKey = BuildConfig.AI_VISION_API_KEY
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .post(reqJson.toRequestBody(mediaType))
            .build()

        Log.d(
            "PowerAi.Trace",
            "VisionBoost(gateway) request START url=$url model=${BuildConfig.AI_VISION_MODEL} imageBytes=${bytes.size} uri=$imageUri"
        )

        return client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w("PowerAi.Trace", "VisionBoost(gateway) HTTP ${resp.code} body=${body.take(600)}")
                throw IllegalStateException("VisionBoost HTTP ${resp.code}: ${body.take(300)}")
            }
            val extracted = VisionBoostJson.extractChatContent(body)
            if (extracted.isBlank()) {
                throw IllegalStateException("VisionBoost empty response: ${body.take(200)}")
            }
            extracted.trim()
        }
    }

    private suspend fun deepSeekValidateMarkdown(markdown: String): String {
        val input = markdown.trim().take(20000)
        if (input.isBlank()) return ""

        val model = BuildConfig.DEEPSEEK_LOGIC_MODEL.trim().ifBlank { "deepseek-chat" }

        val system = """
你是一个严谨的逻辑审计专家。你的任务是对给定的 Markdown 表格/内容做一致性检查。

要求：
- 如果有金额/数量等可计算字段，检查行内/列内的加总是否一致。
- 检查明显的自相矛盾（例如合计与明细不符、单位不一致）。
- 不要编造缺失数据；如果无法验证，明确写出原因。
- 输出用 Markdown：先给出结论（✅/⚠️/❌），然后列出发现的问题与建议。
        """.trimIndent()

        val user = """
请对下面内容做深度逻辑验证（尤其是表格内数据一致性）：

$input
        """.trimIndent()

        val resp = aiApiService.chatCompletions(
            ChatCompletionsRequest(
                model = model,
                stream = false,
                messages = listOf(
                    ChatMessage(role = "system", content = system),
                    ChatMessage(role = "user", content = user)
                )
            )
        )
        val out = resp.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
        return out
    }

    private fun buildPromptForTable(): String {
        return """
你是一个文档结构化助手。请将图片中的表格内容转换成 Markdown 表格。

要求：
- 输出必须是 Markdown 表格（使用 | 分隔）。
- 尽量保持原表格的行列结构与文字内容，不要漏行漏列。
- 如果单元格中有换行，请用 <br/> 保留。
- 不要输出解释、不要加前后缀，只输出 Markdown。
        """.trimIndent()
    }

    private fun buildRequestJson(prompt: String, imageDataUrl: String): String {
        // OpenAI-style multimodal chat format; many providers (incl. GLM-4V compatible gateways) accept this.
        val root = JsonObject()
        root.addProperty("model", BuildConfig.AI_VISION_MODEL)
        root.addProperty("stream", false)

        val messages = com.google.gson.JsonArray()
        val msg = JsonObject()
        msg.addProperty("role", "user")

        val contentArr = com.google.gson.JsonArray()
        val textObj = JsonObject()
        textObj.addProperty("type", "text")
        textObj.addProperty("text", prompt)
        contentArr.add(textObj)

        val imgObj = JsonObject()
        imgObj.addProperty("type", "image_url")
        val imgUrlObj = JsonObject()
        imgUrlObj.addProperty("url", imageDataUrl)
        imgObj.add("image_url", imgUrlObj)
        contentArr.add(imgObj)

        msg.add("content", contentArr)
        messages.add(msg)
        root.add("messages", messages)

        return gson.toJson(root)
    }

    private fun buildVisionUrl(): String {
        val base = BuildConfig.AI_VISION_BASE_URL.trimEnd('/')
        val pathRaw = BuildConfig.AI_VISION_PATH.trim()
        val path = if (pathRaw.startsWith("/")) pathRaw else "/$pathRaw"
        return base + path
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
}
