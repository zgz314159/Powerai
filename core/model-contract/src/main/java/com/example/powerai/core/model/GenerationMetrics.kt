package com.example.powerai.core.model

data class GenerationMetrics(
    val isGenerating: Boolean = false,
    val firstTokenLatencyMs: Long? = null,
    val streamedTokenCount: Int = 0,
    val tokensPerSecond: Double? = null
)
