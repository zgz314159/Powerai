package com.example.powerai.ui.screen.hybrid

import com.example.powerai.data.local.LocalSearchEntry
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.powerai.domain.generator.PromptBuilder

internal object HybridUtils {
    fun sanitizeQuestion(question: String): String {
        var sanitized = question
        try {
            val attempt = String(question.toByteArray(Charset.forName("ISO-8859-1")), Charset.forName("UTF-8"))
            if (attempt.count { it == '?' } < sanitized.count { it == '?' } ||
                attempt.length < sanitized.length) {
                sanitized = attempt
            }
        } catch (_: Throwable) {
        }
        return sanitized
    }

    fun normalizeForFts(sanitized: String): String {
        val norm = sanitized.trim().lowercase()
        return if (norm.endsWith("*")) norm else "${norm}*"
    }

    fun appendSearchHistory(
        history: List<LocalSearchEntry>,
        query: String,
        maxEntries: Int = 50
    ): List<LocalSearchEntry> {
        if (query.isBlank()) return history
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val deduped = history.filter { it.query != trimmed }
        val newList = listOf(LocalSearchEntry(trimmed, now)) + deduped
        return if (newList.size > maxEntries) newList.take(maxEntries) else newList
    }

    fun launchGemmaInference(
        scope: kotlinx.coroutines.CoroutineScope,
        question: String? = null,
        retrievals: List<com.example.powerai.core.model.RetrievalResult>? = null,
        prompt: PromptBuilder.Prompt? = null,
        gemma: com.example.powerai.engine.ai.GemmaLocalInference,
        maxTokens: Int = 180,
        onResult: (String) -> Unit
    ): kotlinx.coroutines.Job {
        return com.example.powerai.engine.ai.GemmaInferenceLauncher.launchGemmaInference(
            scope, question, retrievals, prompt, gemma, maxTokens, onResult
        )
    }
}
