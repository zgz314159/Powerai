package com.example.powerai.domain.usecase

import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.domain.ai.AiPromptProvider
import com.example.powerai.core.model.chat.ChatCompletionsRequest
import com.example.powerai.core.model.chat.ChatMessage
import com.example.powerai.core.repository.AiServiceRepository
import com.example.powerai.domain.repository.WebSearchRepository
import com.example.powerai.domain.repository.WebSearchResult
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class AskAiUseCase @Inject constructor(
    private val aiRepository: AiServiceRepository,
    private val webSearchRepository: WebSearchRepository,
    private val config: RemoteConfigRepository
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
     * ͱزοݣ AI ıش
     */
    suspend fun invoke(question: String, reference: String): String {
        val systemPrompt = AiPromptProvider.buildReferenceWithCitationRules(reference, deviceToday = deviceToday())
        val req = buildChatRequest(systemPrompt, question)
        return callAiAndHandleErrors(req)
    }

    /**
     * AI-only ʣע뱾֤ݣҲҪñš
     * "AI tab  AI"ģʽ
     */
    suspend fun invokeAiOnly(question: String): String {
        val systemPrompt = AiPromptProvider.buildAiOnlyPrompt(deviceToday = deviceToday())
        val req = buildChatRequest(systemPrompt, question)
        return callAiAndHandleErrors(req)
    }

    /**
     * AI + Web SearchȼٰѼΪ"ⲿ"ιģܽᡣ
     * -  webSearchEnabled=true á
     * - δ SERPER_API_KEYȷʾ˵ AI-only
     */
    suspend fun invokeAiSearch(question: String, webSearchEnabled: Boolean): String {
        if (!webSearchEnabled) return invokeAiOnly(question)

        if (!webSearchRepository.isConfigured()) {
            return "δ local.properties  SERPER_API_KEY\n\n" + invokeAiOnly(question)
        }

        val results: List<WebSearchResult> = try {
            webSearchRepository.search(question, count = 5)
        } catch (_: Throwable) {
            emptyList()
        }

        val evidence = formatSearchResults(results)
        val systemPrompt = AiPromptProvider.buildAiWebSearchPrompt(
            deviceToday = deviceToday(),
            searchResults = evidence
        )

        val req = buildChatRequest(systemPrompt, question)

        return try {
            var content = callApiGetContent(req)

            if (content.isBlank()) content = "AI empty response"

            val enriched = enrichContentWithSources(content, results)
            enriched
        } catch (t: Throwable) {
            when (t) {
                is SocketTimeoutException, is HttpException -> invokeAiOnly(question)
                else -> invokeAiOnly(question)
            }
        }
    }

    // Helper methods

    private fun buildChatRequest(systemPrompt: String, userQuestion: String): ChatCompletionsRequest {
        val model = config.getDeepSeekModel().trim().ifBlank { "deepseek-chat" }
        return ChatCompletionsRequest(
            model = model,
            stream = false,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userQuestion)
            )
        )
    }

    private suspend fun callAiAndHandleErrors(req: ChatCompletionsRequest): String {
        return try {
            callApiGetContent(req)
        } catch (t: Throwable) {
            handleApiError(t)
        }
    }

    private suspend fun callApiGetContent(req: ChatCompletionsRequest): String {
        val resp = aiRepository.chatCompletions(req)
        val content = resp.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            .orEmpty()
        return if (content.isNotBlank()) content else "AI empty response"
    }

    private fun handleApiError(t: Throwable): String {
        return when (t) {
            is SocketTimeoutException -> {
                "AI ʱԣѷſʱʱ䣩"
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

    private fun formatSearchResults(results: List<WebSearchResult>): String {
        return results.mapIndexed { idx, r ->
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
    }

    private fun enrichContentWithSources(content: String, results: List<WebSearchResult>): String {
        if (results.isEmpty()) return content

        val hasAnyUrl = results.any { r -> r.url.isNotBlank() && content.contains(r.url, ignoreCase = true) }
        if (hasAnyUrl) return content

        val sources = results
            .take(5)
            .map { it.url }
            .filter { it.isNotBlank() }
            .distinct()

        if (sources.isEmpty()) return content

        return buildString {
            append(content.trim())
            append("\n\nԴ\n")
            sources.forEach { u ->
                append(u)
                append("\n")
            }
        }.trimEnd()
    }
}
