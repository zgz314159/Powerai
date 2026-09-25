package com.example.powerai.domain.util

import com.example.powerai.engine.ai.GemmaFallbackProvider
import com.example.powerai.engine.ai.GemmaResponseSanitizer
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.domain.generator.PromptBuilder
import kotlinx.coroutines.*
import java.nio.charset.Charset

object HybridLogic {
    fun sanitizeQuestion(question: String): String {
        var sanitized = question
        try {
            val attempt = String(question.toByteArray(Charset.forName("ISO-8859-1")), Charset.forName("UTF-8"))
            if (attempt.count { it == '?' } < sanitized.count { it == '?' } ||
                attempt.length < sanitized.length) {
                sanitized = attempt
            }
        } catch (_: Throwable) {}
        return sanitized
    }

    fun launchGemmaInference(
        scope: CoroutineScope,
        prompt: PromptBuilder.Prompt,
        gemma: GemmaLocalInference,
        maxTokens: Int = 180,
        timeoutMs: Long = 45_000L,
        onResult: (String) -> Unit
    ): Job {
        return scope.launch {
            try {
                val safePrompt = PromptBuilder.Prompt(prompt.system, prompt.user.take(1000))
                val inf = withTimeoutOrNull(timeoutMs) {
                    try {
                        gemma.infer(safePrompt, maxTokens)
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (inf != null) {
                    val cleaned = GemmaResponseSanitizer.cleanFinalText(inf.text).first.trim()
                    if (isUsableGemmaText(cleaned)) {
                        onResult(cleaned)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun isUsableGemmaText(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.startsWith("[GemmaLocalInference]")) return false
        if (text.startsWith(GemmaFallbackProvider.MODEL_UNAVAILABLE_MARKER)) return false
        if (text.startsWith("AI 正在思考（本地模型加载中）")) return false
        if (text.startsWith("[系统已截断：输出以复读开始]")) return false
        return true
    }
}
