package com.example.powerai.domain.ai

import com.example.powerai.BuildConfig
import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.remote.api.ChatCompletionsRequest
import com.example.powerai.data.remote.api.ChatMessage
import javax.inject.Inject

/**
 * Lightweight factuality scorer that asks the configured AI model to rate a candidate
 * answer against provided references. Returns a score in [0.0, 1.0].
 *
 * This is a best-effort wrapper; callers should treat network failures as neutral (0.5).
 */
class LlmFactualityScorer @Inject constructor(private val ai: AiApiService, private val observability: com.example.powerai.util.ObservabilityService) {
    suspend fun score(candidate: String, references: List<String>): Double {
        val start = System.currentTimeMillis()
        observability.aiCallStarted("factuality_scorer")
        val model = BuildConfig.DEEPSEEK_LOGIC_MODEL.trim().ifBlank { "deepseek-chat" }
        val prompt = buildString {
            append("请根据以下参考材料判断候选答案的事实性(Factuality)，只输出一个 0.0 到 1.0 之间的小数评分（越接近1越真实），并在第二行给出一句非常简短的理由。\n\n")
            append("候选答案:\n")
            append(candidate)
            append("\n\n参考资料：\n")
            references.take(10).forEachIndexed { idx, r -> append("[R${idx + 1}] " + r + "\n") }
        }

        return try {
            val req = ChatCompletionsRequest(
                model = model,
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                stream = false
            )
            val resp = ai.chatCompletions(req)
            val content = resp.choices?.firstOrNull()?.message?.content.orEmpty()
            // try to extract a floating point number from response
            val m = Regex("([0-1](?:\\.[0-9]+)?)").find(content)
            val v = m?.groups?.get(1)?.value?.toDoubleOrNull()
            val score = v ?: 0.5
            val out = score.coerceIn(0.0, 1.0)
            observability.aiCallFinished("factuality_scorer", System.currentTimeMillis() - start, true)
            out
        } catch (_: Throwable) {
            observability.aiCallFinished("factuality_scorer", System.currentTimeMillis() - start, false)
            0.5
        }
    }
}
