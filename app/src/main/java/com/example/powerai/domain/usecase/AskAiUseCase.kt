package com.example.powerai.domain.usecase

import android.util.Log
import com.example.powerai.BuildConfig
import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.remote.api.ChatCompletionsRequest
import com.example.powerai.data.remote.api.ChatMessage
import com.example.powerai.data.remote.search.GoogleCustomSearchClient
import com.example.powerai.data.remote.search.WebSearchResult
import com.example.powerai.domain.ai.AiPromptProvider
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class AskAiUseCase @Inject constructor(
    private val api: AiApiService,
    private val googleCse: GoogleCustomSearchClient
) {
    private val tag = "AskAiUseCase"

    private fun deviceToday(): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * 接收问题和本地参考内容，返回 AI 文本回答
     */
    suspend fun invoke(question: String, reference: String): String {
        val systemPrompt = AiPromptProvider.buildReferenceWithCitationRules(reference, deviceToday = deviceToday())
        val model = BuildConfig.DEEPSEEK_LOGIC_MODEL.trim().ifBlank { "deepseek-chat" }
        val req = ChatCompletionsRequest(
            model = model,
            stream = false,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = question)
            )
        )
        return try {
            val resp = api.chatCompletions(req)
            val content = resp.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()

            if (content.isNotBlank()) {
                content
            } else {
                "AI empty response"
            }
        } catch (t: Throwable) {
            when (t) {
                is SocketTimeoutException -> {
                    "AI 请求超时：请检查网络后重试（已放宽超时时间）。"
                }
                is HttpException -> {
                    val code = t.code()
                    val body = try { t.response()?.errorBody()?.string() } catch (_: Throwable) { null }
                    val extra = body?.takeIf { it.isNotBlank() }?.take(200)
                    if (extra != null) {
                        "AI service HTTP $code: $extra"
                    } else {
                        "AI service HTTP $code"
                    }
                }
                else -> "AI service error: ${t.message ?: t::class.simpleName}"
            }
        }
    }

    /**
     * AI-only 提问：不注入本地证据，也不要求引用编号。
     * 用于“AI tab 纯 AI”模式。
     */
    suspend fun invokeAiOnly(question: String): String {
        val systemPrompt = AiPromptProvider.buildAiOnlyPrompt(deviceToday = deviceToday())
        val model = BuildConfig.DEEPSEEK_LOGIC_MODEL.trim().ifBlank { "deepseek-chat" }
        val req = ChatCompletionsRequest(
            model = model,
            stream = false,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = question)
            )
        )
        return try {
            val resp = api.chatCompletions(req)
            val content = resp.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()
            if (content.isNotBlank()) content else "AI empty response"
        } catch (t: Throwable) {
            when (t) {
                is SocketTimeoutException -> {
                    "AI 请求超时：请检查网络后重试（已放宽超时时间）。"
                }
                is HttpException -> {
                    val code = t.code()
                    val body = try { t.response()?.errorBody()?.string() } catch (_: Throwable) { null }
                    val extra = body?.takeIf { it.isNotBlank() }?.take(200)
                    if (extra != null) {
                        "AI service HTTP $code: $extra"
                    } else {
                        "AI service HTTP $code"
                    }
                }
                else -> "AI service error: ${t.message ?: t::class.simpleName}"
            }
        }
    }

    /**
     * AI + Web Search：先检索，再把检索结果作为“外部材料”喂给模型总结。
     * - 仅当 webSearchEnabled=true 才启用。
     * - 若未配置 SERPER_API_KEY，则给出明确提示并回退到 AI-only。
     */
    suspend fun invokeAiSearch(question: String, webSearchEnabled: Boolean): String {
        Log.d(tag, "invokeAiSearch enabled=$webSearchEnabled q='${question.take(60)}'")
        if (!webSearchEnabled) return invokeAiOnly(question)

        if (!googleCse.isConfigured()) {
            Log.d(tag, "invokeAiSearch aborted: googleCse not configured")
            // Keep it short; user can configure in local.properties.
            return "未配置联网检索：请在 local.properties 配置 SERPER_API_KEY。\n\n" + invokeAiOnly(question)
        }

        val results: List<WebSearchResult> = try {
            Log.d(tag, "invokeAiSearch calling googleCse.search q='${question.take(80)}' count=5")
            googleCse.search(question, count = 5)
        } catch (_: Throwable) {
            Log.d(tag, "invokeAiSearch googleCse.search threw")
            emptyList()
        }

        Log.d(tag, "invokeAiSearch google results=${results.size}")

        val evidence = results.mapIndexed { idx, r ->
            buildString {
                append("[R")
                append(idx + 1)
                append("] ")
                append(r.title)
                if (r.snippet.isNotBlank()) {
                    append("\n")
                    append(r.snippet)
                }
                append("\n")
                append(r.url)
            }
        }.joinToString("\n\n")

        val systemPrompt = AiPromptProvider.buildAiWebSearchPrompt(
            deviceToday = deviceToday(),
            searchResults = evidence
        )

        Log.d(tag, "invokeAiSearch evidenceLen=${evidence.length}")

        val model = BuildConfig.DEEPSEEK_LOGIC_MODEL.trim().ifBlank { "deepseek-chat" }
        Log.d(tag, "invokeAiSearch model='$model'")
        val req = ChatCompletionsRequest(
            model = model,
            stream = false,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = question)
            )
        )

        return try {
            val resp = api.chatCompletions(req)
            var content = resp.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()

            Log.d(tag, "invokeAiSearch deepseek ok contentLen=${content.length}")

            if (content.isBlank()) content = "AI empty response"

            if (results.isNotEmpty()) {
                val hasAnyUrl = results.any { r -> r.url.isNotBlank() && content.contains(r.url, ignoreCase = true) }
                if (!hasAnyUrl) {
                    val sources = results
                        .take(5)
                        .map { it.url }
                        .filter { it.isNotBlank() }
                        .distinct()
                    if (sources.isNotEmpty()) {
                        content = buildString {
                            append(content.trim())
                            append("\n\n来源：\n")
                            sources.forEach { u ->
                                append(u)
                                append("\n")
                            }
                        }.trimEnd()
                    }
                }
            }

            content
        } catch (t: Throwable) {
            Log.d(tag, "invokeAiSearch deepseek error=${t::class.simpleName}:${t.message}")
            // Search failed? Still return an AI-only fallback rather than hard-failing.
            when (t) {
                is SocketTimeoutException, is HttpException -> invokeAiOnly(question)
                else -> invokeAiOnly(question)
            }
        }
    }
}
