package com.example.powerai.engine.ai

import android.util.Log
import com.example.powerai.core.model.GenerationMetrics

internal object LlamaJniMetrics {
    private const val TAG = "llama_jni_metrics"

    @Volatile
    var generationStartMs: Long = 0L
    var firstTokenLatencyLogged: Boolean = false
    @Volatile
    var streamedTokenCount: Int = 0

    fun stopMetricsSnapshot(
        now: Long,
        metrics: GenerationMetrics,
        lastChunkAtMs: Long,
        lastStopRequestedAtMs: Long,
        finishSequence: Long,
        postStopChunkCount: Int
    ): String {
        val lastChunkAgeMs = (now - lastChunkAtMs).coerceAtLeast(0L)
        val stopAgeMs = if (lastStopRequestedAtMs > 0L) {
            (now - lastStopRequestedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        return "isGenerating=${metrics.isGenerating} streamed=${metrics.streamedTokenCount} firstTokenLatencyMs=${metrics.firstTokenLatencyMs ?: -1} finishSeq=$finishSequence lastChunkAgeMs=$lastChunkAgeMs stopAgeMs=$stopAgeMs postStopChunks=$postStopChunkCount"
    }

    fun streamMetricsSnapshot(
        now: Long,
        lastStreamRequestedAtMs: Long,
        lastStreamExecutorStartedAtMs: Long,
        lastStreamInvokeStartedAtMs: Long,
        lastStreamInvokeReturnedAtMs: Long
    ): String {
        val queueDelayMs = if (lastStreamRequestedAtMs > 0L && lastStreamExecutorStartedAtMs > 0L) {
            (lastStreamExecutorStartedAtMs - lastStreamRequestedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val invokeDelayMs = if (lastStreamExecutorStartedAtMs > 0L && lastStreamInvokeStartedAtMs > 0L) {
            (lastStreamInvokeStartedAtMs - lastStreamExecutorStartedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val invokeReturnMs = if (lastStreamInvokeStartedAtMs > 0L && lastStreamInvokeReturnedAtMs > 0L) {
            (lastStreamInvokeReturnedAtMs - lastStreamInvokeStartedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val sinceRequestMs = if (lastStreamRequestedAtMs > 0L) {
            (now - lastStreamRequestedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        return "queueDelayMs=$queueDelayMs invokeDelayMs=$invokeDelayMs invokeReturnMs=$invokeReturnMs sinceRequestMs=$sinceRequestMs"
    }

    fun logStreamBottleneckAssessment(
        now: Long,
        phase: String,
        lastStreamRequestedAtMs: Long,
        lastStreamExecutorStartedAtMs: Long,
        lastStreamInvokeStartedAtMs: Long,
        lastStreamInvokeReturnedAtMs: Long
    ) {
        val queueDelayMs = if (lastStreamRequestedAtMs > 0L && lastStreamExecutorStartedAtMs > 0L) {
            (lastStreamExecutorStartedAtMs - lastStreamRequestedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val invokeDelayMs = if (lastStreamExecutorStartedAtMs > 0L && lastStreamInvokeStartedAtMs > 0L) {
            (lastStreamInvokeStartedAtMs - lastStreamExecutorStartedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val invokeReturnMs = if (lastStreamInvokeStartedAtMs > 0L && lastStreamInvokeReturnedAtMs > 0L) {
            (lastStreamInvokeReturnedAtMs - lastStreamInvokeStartedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val sinceRequestMs = if (lastStreamRequestedAtMs > 0L) {
            (now - lastStreamRequestedAtMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val assessment = when {
            phase == "first_token" && queueDelayMs in 0..5 && invokeDelayMs in 0..5 && invokeReturnMs < 0 -> {
                "first token delay is inside AAR/native generate call before first callback"
            }
            phase == "invoke_return" && invokeReturnMs >= 0L && invokeReturnMs + 250L >= sinceRequestMs -> {
                "AAR/native generate call stayed blocking until stream completion"
            }
            queueDelayMs > 25L -> {
                "request spent noticeable time queued before AAR executor started"
            }
            invokeDelayMs > 25L -> {
                "request spent noticeable time before invoking AAR generate method"
            }
            else -> {
                "no clear bottleneck classification"
            }
        }
        val metricsStr = streamMetricsSnapshot(now, lastStreamRequestedAtMs, lastStreamExecutorStartedAtMs, lastStreamInvokeStartedAtMs, lastStreamInvokeReturnedAtMs)
        Log.i(TAG, "stream assessment phase=$phase $assessment; $metricsStr")
    }
}
