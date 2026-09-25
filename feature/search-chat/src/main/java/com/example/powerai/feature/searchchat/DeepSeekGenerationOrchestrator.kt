package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.core.model.SmartThreadBenchmarkResult
import com.example.powerai.core.model.SmartThreadPreset
import android.os.SystemClock
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.engine.ai.DeepSeekTextCodec
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import com.example.powerai.engine.ai.ThinkingInterceptor
import com.example.powerai.engine.ai.SmartAbRunRecord
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.engine.ai.DeepSeekPromptProfiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class DeepSeekGenerationOrchestrator @Inject constructor() {

    interface GenerationDelegate {
        val state: DeepSeekUiState
        val provider: NativeResourceProvider
        fun reduceState(reducer: DeepSeekUiState.() -> DeepSeekUiState)
        fun updateProgress(phase: SmartProgressPhase, statusText: String, detailText: String, showAsActive: Boolean = true)
        fun isRunActive(runToken: String, sessionId: String): Boolean
        fun cachePromptEnabled(): Boolean
        val CACHE_REUSE_MIN_TOKENS: Int
        fun isContextOverflowMessage(chunk: String): Boolean
    }

    suspend fun runGeneration(
        delegate: GenerationDelegate,
        engine: PowerAIEngine?,
        runToken: String,
        prompt: String,
        maxTokens: Int,
        answerMode: SmartAnswerMode,
        sessionId: String,
        query: String,
        startedAtEpochMs: Long,
        benchmarkKind: String? = null,
        benchmarkGroupId: String? = null,
        benchmarkOrder: Int? = null,
        prefixReuseHit: Boolean = false,
        stateSnapshotReuseHit: Boolean = false,
        stateSnapshotBytes: Int = 0
    ): SmartGenerationRunResult = coroutineScope {
        try {
            if (engine == null || !engine.isLoaded()) {
                delegate.reduceState { copy(result = "模型未加载。") }
                logBenchmarkRecord(delegate, sessionId, query, answerMode, startedAtEpochMs, "error", "模型未加载。", benchmarkKind, benchmarkGroupId, benchmarkOrder, prefixReuseHit, stateSnapshotReuseHit, stateSnapshotBytes)
                return@coroutineScope SmartGenerationRunResult(status = "error", errorMessage = "模型未加载。")
            }

            val systemPrompt = if (answerMode.thinkingEnabled) {
                DeepSeekPromptProfiles.DEEP_SYSTEM_PROMPT
            } else {
                DeepSeekPromptProfiles.QUICK_SYSTEM_PROMPT
            }
            engine.configureStreamingThinking(answerMode.thinkingEnabled)
            engine.setSystemPrompt(systemPrompt)

            delegate.updateProgress(
                phase = SmartProgressPhase.WAITING_FIRST_TOKEN,
                statusText = "模型正在加载上下文并整理答案",
                detailText = "首个可见答案出来前，底层仍会先做预填充和上下文整理。"
            )

            val generationStartedAt = SystemClock.elapsedRealtime()
            val answerStartLatencyMs = AtomicLong(-1L)

            val collectorJob = launch(Dispatchers.Main) {
                var inThinkBlock = false
                var pendingThinkingText = ""
                var pendingTagFragment = ""
                val suppressedMetaReasoning = StringBuilder()
                var answerStarted = false
                var chunkIndex = 0
                val rawVisibleAnswer = StringBuilder()

                engine.chunkFlow?.collect { chunk ->
                    if (!delegate.isRunActive(runToken, sessionId)) return@collect
                    chunkIndex += 1
                    if (delegate.isContextOverflowMessage(chunk)) return@collect

                    val parsed = ThinkingInterceptor.parseStreamingChunk(
                        text = chunk,
                        inThinkBlock = inThinkBlock,
                        pendingThinkingText = pendingThinkingText,
                        pendingTagFragment = pendingTagFragment
                    )
                    inThinkBlock = parsed.inThinkBlock
                    pendingThinkingText = parsed.pendingThinkingText
                    pendingTagFragment = parsed.pendingTagFragment

                    if (!delegate.isRunActive(runToken, sessionId)) return@collect

                    fun buildPreviewText(currentState: DeepSeekUiState): String {
                        val pendingReasoning = suppressedMetaReasoning.toString().trim()
                        val pendingThinking = DeepSeekTextCodec.normalizeFinalAnswer(pendingThinkingText).trim()
                        return buildList {
                            addAll(currentState.thinking)
                            if (pendingThinking.isNotEmpty()) add(pendingThinking)
                            if (pendingReasoning.isNotEmpty()) add(pendingReasoning)
                        }.joinToString("\n\n")
                    }

                    val visibleText = if (answerStarted) {
                        parsed.visibleText
                    } else {
                        SmartAnswerGuard.stripMetaReasoningLead(parsed.visibleText)
                    }
                    val suppressAsReasoningLead = !answerStarted &&
                        visibleText.isBlank() &&
                        SmartAnswerGuard.isMetaReasoningLead(parsed.visibleText)
                    val isFirstVisibleChunk = !answerStarted && visibleText.isNotBlank()

                    if (parsed.completedThinkingSegments.isNotEmpty()) {
                        delegate.reduceState {
                            copy(thinking = (thinking + parsed.completedThinkingSegments.map(DeepSeekTextCodec::normalizeFinalAnswer)).distinct())
                        }
                    }

                    if (!answerStarted) {
                        if (suppressAsReasoningLead) {
                            suppressedMetaReasoning.append(parsed.visibleText)
                            val previewText = buildPreviewText(delegate.state)
                            delegate.reduceState { copy(thinkingPreview = previewText) }
                            delegate.updateProgress(
                                phase = SmartProgressPhase.THINKING,
                                statusText = "模型正在整理答案",
                                detailText = previewText.take(120).ifBlank { "已识别到前置推理内容，继续等待正式答案。" }
                            )
                        } else if (visibleText.isNotBlank()) {
                            answerStarted = true
                            delegate.reduceState { copy(thinkingPreview = "") }
                        } else {
                            val previewText = buildPreviewText(delegate.state)
                            delegate.reduceState { copy(thinkingPreview = previewText) }
                            delegate.updateProgress(
                                phase = SmartProgressPhase.THINKING,
                                statusText = "模型正在思考并整理答案",
                                detailText = previewText.take(120).ifBlank { "已开始返回内部思考内容，仍在等待最终答案。" }
                            )
                        }
                    }

                    if (visibleText.isNotBlank()) {
                        if (isFirstVisibleChunk) {
                            val firstVisibleLatencyMs = SystemClock.elapsedRealtime() - generationStartedAt
                            answerStartLatencyMs.compareAndSet(-1L, firstVisibleLatencyMs)
                            SmartDeepSeekDebugLogger.logEvent(delegate.provider, "ANSWER_START", "chunkIndex=$chunkIndex latencyMs=$firstVisibleLatencyMs")
                            delegate.updateProgress(
                                phase = SmartProgressPhase.ANSWERING,
                                statusText = "已开始输出答案",
                                detailText = "首个可见内容耗时 ${firstVisibleLatencyMs}ms。"
                            )
                        }
                        rawVisibleAnswer.append(visibleText)
                        delegate.reduceState { copy(result = DeepSeekTextCodec.normalizeFinalAnswer(rawVisibleAnswer.toString())) }
                    }
                }
            }

            val out = withContext(Dispatchers.IO) {
                engine.infer(prompt, maxTokens, stream = true)
            }
            collectorJob.cancel()

            coroutineContext.ensureActive()
            if (!delegate.isRunActive(runToken, sessionId)) {
                throw CancellationException("stale generation ignored")
            }

            val extractedThinking = ThinkingInterceptor.extractThinkingSegments(out).distinct()
            if (extractedThinking.isNotEmpty()) {
                delegate.reduceState { copy(thinking = extractedThinking) }
            }

            val finalVisibleAnswer = DeepSeekTextCodec.normalizeFinalAnswer(
                ThinkingInterceptor.stripThinkingSegments(out)
            )
            val streamedChunks = engine.metrics.value.streamedTokenCount
            val resolvedFinalAnswer = SmartAnswerGuard.stripMetaReasoningLead(finalVisibleAnswer)

            val currentVisibleAnswer = delegate.state.result
            if (resolvedFinalAnswer.isNotBlank() && (currentVisibleAnswer.isBlank() || resolvedFinalAnswer.length > currentVisibleAnswer.length)) {
                delegate.reduceState { copy(result = resolvedFinalAnswer) }
            }

            delegate.reduceState { copy(thinkingPreview = "") }

            val firstTokenLatencyMs = engine.metrics.value.firstTokenLatencyMs ?: 0L
            val decodeWindowMs = (SystemClock.elapsedRealtime() - generationStartedAt - firstTokenLatencyMs).coerceAtLeast(1L)
            val decodeCharsPerSecond = if (resolvedFinalAnswer.isBlank()) null else resolvedFinalAnswer.length * 1000.0 / decodeWindowMs.toDouble()

            delegate.reduceState { copy(stageMetrics = stageMetrics.copy(decodeCharsPerSecond = decodeCharsPerSecond)) }
            delegate.updateProgress(phase = SmartProgressPhase.COMPLETED, statusText = "回答完成", detailText = "共接收 $streamedChunks 个片段。", showAsActive = false)

            logBenchmarkRecord(delegate, sessionId, query, answerMode, startedAtEpochMs, "completed", null, benchmarkKind, benchmarkGroupId, benchmarkOrder, prefixReuseHit, stateSnapshotReuseHit, stateSnapshotBytes, firstTokenLatencyMs, answerStartLatencyMs.get(), decodeCharsPerSecond, streamedChunks, out.length, resolvedFinalAnswer.length)

            SmartGenerationRunResult(
                status = "completed",
                firstTokenLatencyMs = firstTokenLatencyMs,
                answerStartLatencyMs = answerStartLatencyMs.get().takeIf { it >= 0L }
            )
        } catch (t: CancellationException) {
            logBenchmarkRecord(delegate, sessionId, query, answerMode, startedAtEpochMs, "cancelled", t.message, benchmarkKind, benchmarkGroupId, benchmarkOrder, prefixReuseHit, stateSnapshotReuseHit, stateSnapshotBytes)
            SmartGenerationRunResult(status = "cancelled", errorMessage = t.message)
        } catch (t: Throwable) {
            delegate.reduceState { copy(result = "推理失败: ${t.message}", thinkingPreview = "") }
            delegate.updateProgress(phase = SmartProgressPhase.ERROR, statusText = "推理失败", detailText = t.message.orEmpty(), showAsActive = false)
            logBenchmarkRecord(delegate, sessionId, query, answerMode, startedAtEpochMs, "error", t.message, benchmarkKind, benchmarkGroupId, benchmarkOrder, prefixReuseHit, stateSnapshotReuseHit, stateSnapshotBytes)
            SmartGenerationRunResult(status = "error", errorMessage = t.message)
        }
    }

    private fun logBenchmarkRecord(
        delegate: GenerationDelegate,
        sessionId: String,
        query: String,
        answerMode: SmartAnswerMode,
        startedAtEpochMs: Long,
        status: String,
        errorMessage: String?,
        benchmarkKind: String?,
        benchmarkGroupId: String?,
        benchmarkOrder: Int?,
        prefixReuseHit: Boolean,
        stateSnapshotReuseHit: Boolean,
        stateSnapshotBytes: Int,
        firstTokenLatencyMs: Long? = null,
        answerStartLatencyMs: Long? = null,
        decodeCharsPerSecond: Double? = null,
        streamedChunkCount: Int = 0,
        rawOutputLength: Int = 0,
        finalAnswerLength: Int = 0
    ) {
        val stageSnapshot = delegate.state.stageMetrics
        SmartDeepSeekDebugLogger.appendBenchmarkRecord(
            provider = delegate.provider,
            record = SmartAbRunRecord(
                sessionId = sessionId,
                status = status,
                query = query,
                answerMode = answerMode.label,
                backendMode = delegate.state.backendMode.label,
                threadMode = delegate.state.threadPreset.label,
                batchMode = stageSnapshot.batchLabel,
                batchThreadMode = stageSnapshot.batchThreadLabel,
                startedAtEpochMs = startedAtEpochMs,
                finishedAtEpochMs = System.currentTimeMillis(),
                retrievalMs = stageSnapshot.retrievalMs,
                promptBuildMs = stageSnapshot.promptBuildMs,
                firstTokenLatencyMs = firstTokenLatencyMs,
                answerStartLatencyMs = answerStartLatencyMs.takeIf { it != null && it >= 0L },
                decodeCharsPerSecond = decodeCharsPerSecond,
                streamedChunkCount = streamedChunkCount,
                rawOutputLength = rawOutputLength,
                finalAnswerLength = finalAnswerLength,
                evidenceCount = stageSnapshot.evidenceCount,
                promptChars = stageSnapshot.promptChars,
                errorMessage = errorMessage,
                prefixReuseEnabled = stageSnapshot.prefixReuseEnabled,
                prefixReuseHit = prefixReuseHit,
                stateSnapshotReuseEnabled = delegate.state.stateSnapshotReuseEnabled,
                stateSnapshotReuseHit = stateSnapshotReuseHit,
                stateSnapshotBytes = stateSnapshotBytes,
                cachePromptEnabled = delegate.cachePromptEnabled(),
                cacheReuseMinTokens = if (delegate.cachePromptEnabled()) delegate.CACHE_REUSE_MIN_TOKENS else 0,
                benchmarkKind = benchmarkKind,
                benchmarkGroupId = benchmarkGroupId,
                benchmarkOrder = benchmarkOrder
            )
        )
    }
}
