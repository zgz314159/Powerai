package com.example.powerai.engine.ai

import com.example.powerai.core.model.GenerationMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages generation metrics and timing for native model execution.
 */
object LlamaMetricsManager {
    private val _generationMetrics = MutableStateFlow(GenerationMetrics())
    val generationMetrics: StateFlow<GenerationMetrics> = _generationMetrics.asStateFlow()

    private val _finishSequence = MutableStateFlow(0L)
    val finishSequence: StateFlow<Long> = _finishSequence.asStateFlow()

    private val _lastChunkAtMs = MutableStateFlow(0L)
    val lastChunkAtMs: StateFlow<Long> = _lastChunkAtMs.asStateFlow()

    var generationStartMs: Long = 0L
    var firstTokenLatencyLogged: Boolean = false
    var streamedTokenCount: Int = 0

    fun updateMetrics(reducer: (GenerationMetrics) -> GenerationMetrics) {
        _generationMetrics.value = reducer(_generationMetrics.value)
    }

    fun reset() {
        _generationMetrics.value = GenerationMetrics()
        generationStartMs = 0L
        firstTokenLatencyLogged = false
        streamedTokenCount = 0
    }

    fun incrementFinishSequence() {
        _finishSequence.value += 1
    }

    fun updateLastChunkTime(timeMs: Long) {
        _lastChunkAtMs.value = timeMs
    }
}
