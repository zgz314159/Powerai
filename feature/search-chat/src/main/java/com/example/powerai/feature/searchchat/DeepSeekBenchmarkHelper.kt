package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.core.model.SmartThreadBenchmarkResult
import com.example.powerai.core.model.SmartThreadPreset
import android.content.Context
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import com.example.powerai.core.model.NativeResourceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class DeepSeekBenchmarkHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeResourceProvider: NativeResourceProvider
) {
    fun buildThreadBenchmarkSummary(results: List<SmartThreadBenchmarkResult>): String {
        val completed = results.filter { it.status == "completed" && it.firstTokenLatencyMs != null }
        if (completed.isEmpty()) {
            return if (results.isEmpty()) {
                "线程基准未产出有效结果。"
            } else {
                results.joinToString(" | ") { result ->
                    "${result.threadLabel}:${result.status}${result.errorMessage?.let { "(${it})" } ?: ""}"
                }
            }
        }
        val fastest = completed.minByOrNull { it.firstTokenLatencyMs ?: Long.MAX_VALUE }
        val breakdown = results.joinToString(" | ") { result ->
            val latencyText = result.firstTokenLatencyMs?.let { "${it}ms" } ?: result.status
            "${result.threadLabel}:$latencyText"
        }
        return if (fastest == null) breakdown else "$breakdown；最快：${fastest.threadLabel} = ${fastest.firstTokenLatencyMs}ms"
    }

    fun buildPrefillBenchmarkSummary(results: List<SmartPrefillBenchmarkResult>): String {
        val completed = results.filter { it.status == "completed" && it.firstTokenLatencyMs != null }
        if (completed.isEmpty()) {
            return if (results.isEmpty()) {
                "prefill 基准未产出有效结果。"
            } else {
                results.joinToString(" | ") { result ->
                    "${result.dimensionLabel}:${result.status}${result.errorMessage?.let { "(${it})" } ?: ""}"
                }
            }
        }
        val fastest = completed.minByOrNull { it.firstTokenLatencyMs ?: Long.MAX_VALUE }
        val breakdown = results.joinToString(" | ") { result ->
            val latencyText = result.firstTokenLatencyMs?.let { "${it}ms" } ?: result.status
            "${result.dimensionLabel}:$latencyText"
        }
        return if (fastest == null) breakdown else "$breakdown；最快：${fastest.dimensionLabel} = ${fastest.firstTokenLatencyMs}ms"
    }

    fun logEvent(tag: String, message: String) {
        SmartDeepSeekDebugLogger.logEvent(nativeResourceProvider, tag, message)
    }
}
