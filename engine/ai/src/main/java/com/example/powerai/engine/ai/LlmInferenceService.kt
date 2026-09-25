package com.example.powerai.engine.ai

import com.example.powerai.domain.generator.PromptBuilder

/**
 * Minimal LLM inference service interface for future model adapters (Gemma, Llama, etc.).
 * Accepts the PromptBuilder.Prompt object directly so PromptBuilder output can be passed in.
 */
interface LlmInferenceService {
    suspend fun infer(prompt: PromptBuilder.Prompt, maxTokens: Int = 512): InferenceResult
}

/**
 * Simple container for inference results.
 */
data class InferenceResult(
    val text: String,
    val tokensUsed: Int = 0,
    val raw: String? = null
)
