package com.example.powerai.engine.ai

import com.example.powerai.domain.generator.PromptBuilder
import com.example.powerai.core.model.RetrievalResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object GemmaInferenceLauncher {
    fun launchGemmaInference(
        scope: CoroutineScope,
        question: String? = null,
        retrievals: List<RetrievalResult>? = null,
        prompt: PromptBuilder.Prompt? = null,
        gemma: GemmaLocalInference,
        maxTokens: Int = 180,
        onResult: (String) -> Unit
    ): Job {
        return scope.launch {
            try {
                val finalPrompt = prompt ?: if (question != null && retrievals != null) {
                    val topOne = if (retrievals.isNotEmpty()) listOf(retrievals[0]) else emptyList()
                    PromptBuilder().buildPrompt(question, topOne)
                } else null

                if (finalPrompt != null) {
                    val safePrompt = PromptBuilder.Prompt(finalPrompt.system, finalPrompt.user.take(1500))
                    val inf = try { gemma.infer(safePrompt, maxTokens) } catch (t: Throwable) { null }
                    if (inf != null) {
                        onResult(inf.text)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
}
