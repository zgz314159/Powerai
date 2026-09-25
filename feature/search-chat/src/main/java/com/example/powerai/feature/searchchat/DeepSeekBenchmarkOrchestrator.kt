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
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekBenchmarkOrchestrator @Inject constructor(
    private val promptManager: DeepSeekPromptManager
) {
    companion object {
        val THREAD_BENCHMARK_PRESETS = listOf(
            SmartThreadPreset.FOUR,
            SmartThreadPreset.SIX,
            SmartThreadPreset.EIGHT
        )
        val BATCH_SIZE_BENCHMARK_PRESETS = listOf(
            SmartBatchSizePreset.B32,
            SmartBatchSizePreset.B64,
            SmartBatchSizePreset.B128,
            SmartBatchSizePreset.B256
        )
        val BATCH_THREAD_BENCHMARK_PRESETS = listOf(
            SmartBatchThreadPreset.TWO,
            SmartBatchThreadPreset.FOUR,
            SmartBatchThreadPreset.SIX,
            SmartBatchThreadPreset.EIGHT
        )
    }

    interface BenchmarkDelegate {
        val state: DeepSeekUiState
        val engine: PowerAIEngine?
        val provider: NativeResourceProvider
        fun reduceState(reducer: DeepSeekUiState.() -> DeepSeekUiState)
        fun updateProgress(phase: SmartProgressPhase, statusText: String, detailText: String, showAsActive: Boolean)
        suspend fun runGenerationStep(
            prompt: String,
            maxTokens: Int,
            answerMode: SmartAnswerMode,
            sessionId: String,
            query: String,
            benchmarkKind: String,
            benchmarkGroupId: String,
            benchmarkOrder: Int,
            prefixReuseHit: Boolean,
            stateSnapshotHit: Boolean,
            stateSnapshotBytes: Int
        ): SmartGenerationRunResult
        suspend fun prepareForNextGeneration()
        fun applyThreadPresetSynchronously(preset: SmartThreadPreset)
        fun applyBatchSizePresetSynchronously(preset: SmartBatchSizePreset)
        fun applyBatchThreadPresetSynchronously(preset: SmartBatchThreadPreset)
        fun openSmartSession(question: String, mode: SmartAnswerMode): String
        suspend fun prepareExplicitStateSnapshot(promptPrefixKey: String): Pair<Boolean, Int>
        suspend fun prepareGroundedPrompt(question: String, mode: SmartAnswerMode): PreparedGroundedPrompt
        fun cachePromptEnabled(): Boolean
        val CACHE_REUSE_MIN_TOKENS: Int
        var stopRequested: Boolean
    }

    suspend fun runThreadLatencyBenchmark(question: String, delegate: BenchmarkDelegate) {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isBlank()) {
            delegate.reduceState {
                copy(threadBenchmarkState = SmartThreadBenchmarkUiState(statusText = "线程基准需要先输入一个真实问题。"))
            }
            return
        }
        if (delegate.state.backendMode != SmartBackendMode.CPU) {
            delegate.reduceState {
                copy(threadBenchmarkState = SmartThreadBenchmarkUiState(statusText = "线程基准仅支持 CPU 稳定后端，请先切换到 CPU。"))
            }
            return
        }
        if (!delegate.state.modelLoaded || delegate.engine?.isLoaded() != true) {
            delegate.reduceState {
                copy(threadBenchmarkState = SmartThreadBenchmarkUiState(statusText = "线程基准前请先加载模型。"))
            }
            return
        }
        if (SmartAnswerGuard.resolveFastPath(normalizedQuestion) != null) {
            delegate.reduceState {
                copy(threadBenchmarkState = SmartThreadBenchmarkUiState(statusText = "当前问题会命中快捷直答，无法比较模型 first token。请换一个需要真实推理的问题。"))
            }
            return
        }

        val benchmarkId = UUID.randomUUID().toString()
        val originalPreset = delegate.state.threadPreset
        val currentMode = delegate.state.answerMode
        val results = mutableListOf<SmartThreadBenchmarkResult>()
        delegate.reduceState {
            copy(threadBenchmarkState = SmartThreadBenchmarkUiState(isRunning = true, statusText = "正在准备 4/6/8 线程基准", results = emptyList()))
        }
        SmartDeepSeekDebugLogger.logEvent(
            provider = delegate.provider,
            tag = "THREAD_BENCH",
            message = "start groupId=$benchmarkId question=$normalizedQuestion mode=${currentMode.label} backend=${delegate.state.backendMode.label} presets=${THREAD_BENCHMARK_PRESETS.joinToString(",") { it.label }}"
        )

        try {
            delegate.prepareForNextGeneration()
            val askedAt = System.currentTimeMillis()
            delegate.reduceState { copy(askedAtMillis = askedAt) }
            val preparedPrompt = delegate.prepareGroundedPrompt(normalizedQuestion, currentMode)
            for ((index, preset) in THREAD_BENCHMARK_PRESETS.withIndex()) {
                currentCoroutineContext().ensureActive()
                delegate.reduceState {
                    copy(threadBenchmarkState = SmartThreadBenchmarkUiState(isRunning = true, statusText = "正在跑 ${preset.label} first token 基准（${index + 1}/${THREAD_BENCHMARK_PRESETS.size}）", results = results.toList()))
                }
                delegate.applyThreadPresetSynchronously(preset)
                delegate.reduceState {
                    copy(
                        smartReferences = preparedPrompt.references,
                        stageMetrics = preparedPrompt.stageMetrics(
                            threadLabel = preset.label,
                            batchLabel = delegate.state.batchSizePreset.label,
                            batchThreadLabel = delegate.state.batchThreadPreset.label,
                            prefixReuseEnabled = delegate.state.prefixReuseEnabled
                        )
                    )
                }
                val (stateSnapshotHit, stateSnapshotBytes) = delegate.prepareExplicitStateSnapshot(preparedPrompt.promptPrefixKey)
                val sessionId = delegate.openSmartSession(normalizedQuestion, currentMode)
                val runResult = delegate.runGenerationStep(
                    prompt = preparedPrompt.prompt,
                    maxTokens = currentMode.maxTokens,
                    answerMode = currentMode,
                    sessionId = sessionId,
                    query = normalizedQuestion,
                    benchmarkKind = "thread_latency",
                    benchmarkGroupId = benchmarkId,
                    benchmarkOrder = index + 1,
                    prefixReuseHit = preparedPrompt.prefixReuseHit,
                    stateSnapshotHit = stateSnapshotHit,
                    stateSnapshotBytes = stateSnapshotBytes
                )
                val benchmarkResult = SmartThreadBenchmarkResult(
                    threadLabel = preset.label,
                    status = runResult.status,
                    firstTokenLatencyMs = runResult.firstTokenLatencyMs,
                    answerStartLatencyMs = runResult.answerStartLatencyMs,
                    errorMessage = runResult.errorMessage
                )
                results += benchmarkResult
                SmartDeepSeekDebugLogger.logEvent(
                    provider = delegate.provider,
                    tag = "THREAD_BENCH",
                    message = "result groupId=$benchmarkId preset=${preset.label} status=${runResult.status} firstTokenLatencyMs=${runResult.firstTokenLatencyMs ?: -1} answerStartLatencyMs=${runResult.answerStartLatencyMs ?: -1} error=${runResult.errorMessage.orEmpty()}"
                )
            }

            val summary = buildThreadBenchmarkSummary(results)
            delegate.reduceState {
                copy(threadBenchmarkState = SmartThreadBenchmarkUiState(isRunning = false, statusText = summary, results = results.toList()))
            }
            delegate.updateProgress(phase = SmartProgressPhase.COMPLETED, statusText = "线程基准完成", detailText = summary, showAsActive = false)
            SmartDeepSeekDebugLogger.logEvent(provider = delegate.provider, tag = "THREAD_BENCH", message = "completed groupId=$benchmarkId summary=$summary")
        } catch (t: CancellationException) {
            val message = if (delegate.stopRequested) "线程基准已取消，底层停止流程已接管" else "线程基准已取消。"
            delegate.reduceState { copy(threadBenchmarkState = SmartThreadBenchmarkUiState(isRunning = false, statusText = message, results = results.toList())) }
        } catch (t: Throwable) {
            val message = "线程基准失败: ${t.message}"
            delegate.reduceState { copy(threadBenchmarkState = SmartThreadBenchmarkUiState(isRunning = false, statusText = message, results = results.toList())) }
        } finally {
            delegate.applyThreadPresetSynchronously(originalPreset)
        }
    }

    suspend fun runBatchSizeLatencyBenchmark(question: String, delegate: BenchmarkDelegate) {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isBlank()) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "prefill 基准需要先输入一个真实问题。")) }
            return
        }
        if (delegate.state.backendMode != SmartBackendMode.CPU) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "prefill 基准仅支持 CPU 稳定后端，请先切换到 CPU。")) }
            return
        }
        if (!delegate.state.modelLoaded || delegate.engine?.isLoaded() != true) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "prefill 基准前请先加载模型。")) }
            return
        }
        if (SmartAnswerGuard.resolveFastPath(normalizedQuestion) != null) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "当前问题会命中快捷直答，无法比较模型 first token。请换一个需要真实推理的问题。")) }
            return
        }

        val benchmarkId = UUID.randomUUID().toString()
        val originalBatchPreset = delegate.state.batchSizePreset
        val currentMode = delegate.state.answerMode
        val results = mutableListOf<SmartPrefillBenchmarkResult>()
        delegate.reduceState {
            copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = true, benchmarkTitle = "n_batch 基准", statusText = "正在准备 n_batch 基准", results = emptyList()))
        }

        try {
            delegate.prepareForNextGeneration()
            val askedAt = System.currentTimeMillis()
            delegate.reduceState { copy(askedAtMillis = askedAt) }
            val preparedPrompt = delegate.prepareGroundedPrompt(normalizedQuestion, currentMode)
            for ((index, preset) in BATCH_SIZE_BENCHMARK_PRESETS.withIndex()) {
                currentCoroutineContext().ensureActive()
                delegate.reduceState {
                    copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = true, benchmarkTitle = "n_batch 基准", statusText = "正在跑 ${preset.label}（${index + 1}/${BATCH_SIZE_BENCHMARK_PRESETS.size}）", results = results.toList()))
                }
                delegate.applyBatchSizePresetSynchronously(preset)
                delegate.reduceState {
                    copy(
                        smartReferences = preparedPrompt.references,
                        stageMetrics = preparedPrompt.stageMetrics(
                            threadLabel = delegate.state.threadPreset.label,
                            batchLabel = preset.label,
                            batchThreadLabel = delegate.state.batchThreadPreset.label,
                            prefixReuseEnabled = delegate.state.prefixReuseEnabled
                        )
                    )
                }
                val (stateSnapshotHit, stateSnapshotBytes) = delegate.prepareExplicitStateSnapshot(preparedPrompt.promptPrefixKey)
                val sessionId = delegate.openSmartSession(normalizedQuestion, currentMode)
                val runResult = delegate.runGenerationStep(
                    prompt = preparedPrompt.prompt,
                    maxTokens = currentMode.maxTokens,
                    answerMode = currentMode,
                    sessionId = sessionId,
                    query = normalizedQuestion,
                    benchmarkKind = "prefill_batch_size",
                    benchmarkGroupId = benchmarkId,
                    benchmarkOrder = index + 1,
                    prefixReuseHit = preparedPrompt.prefixReuseHit,
                    stateSnapshotHit = stateSnapshotHit,
                    stateSnapshotBytes = stateSnapshotBytes
                )
                results += SmartPrefillBenchmarkResult(
                    dimensionLabel = preset.label,
                    status = runResult.status,
                    firstTokenLatencyMs = runResult.firstTokenLatencyMs,
                    answerStartLatencyMs = runResult.answerStartLatencyMs,
                    errorMessage = runResult.errorMessage
                )
            }
            updatePrefillSummary(results, "n_batch 基准", delegate)
        } catch (t: CancellationException) {
            val msg = if (delegate.stopRequested) "n_batch 基准已取消，底层停止流程已接管" else "n_batch 基准已取消。"
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = false, benchmarkTitle = "n_batch 基准", statusText = msg, results = results.toList())) }
        } catch (t: Throwable) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = false, benchmarkTitle = "n_batch 基准", statusText = "n_batch 基准失败: ${t.message}", results = results.toList())) }
        } finally {
            delegate.applyBatchSizePresetSynchronously(originalBatchPreset)
        }
    }

    suspend fun runBatchThreadLatencyBenchmark(question: String, delegate: BenchmarkDelegate) {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isBlank()) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "prefill 基准需要先输入一个真实问题。")) }
            return
        }
        if (delegate.state.backendMode != SmartBackendMode.CPU) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "prefill 基准仅支持 CPU 稳定后端，请先切换到 CPU。")) }
            return
        }
        if (!delegate.state.modelLoaded || delegate.engine?.isLoaded() != true) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "prefill 基准前请先加载模型。")) }
            return
        }
        if (SmartAnswerGuard.resolveFastPath(normalizedQuestion) != null) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(statusText = "当前问题会命中快捷直答，无法比较模型 first token。请换一个需要真实推理的问题。")) }
            return
        }

        val benchmarkId = UUID.randomUUID().toString()
        val originalBatchThreadPreset = delegate.state.batchThreadPreset
        val currentMode = delegate.state.answerMode
        val results = mutableListOf<SmartPrefillBenchmarkResult>()
        delegate.reduceState {
            copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = true, benchmarkTitle = "n_threads_batch 基准", statusText = "正在准备 n_threads_batch 基准", results = emptyList()))
        }

        try {
            delegate.prepareForNextGeneration()
            val askedAt = System.currentTimeMillis()
            delegate.reduceState { copy(askedAtMillis = askedAt) }
            val preparedPrompt = delegate.prepareGroundedPrompt(normalizedQuestion, currentMode)
            for ((index, preset) in BATCH_THREAD_BENCHMARK_PRESETS.withIndex()) {
                currentCoroutineContext().ensureActive()
                delegate.reduceState {
                    copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = true, benchmarkTitle = "n_threads_batch 基准", statusText = "正在跑 ${preset.label}（${index + 1}/${BATCH_THREAD_BENCHMARK_PRESETS.size}）", results = results.toList()))
                }
                delegate.applyBatchThreadPresetSynchronously(preset)
                delegate.reduceState {
                    copy(
                        smartReferences = preparedPrompt.references,
                        stageMetrics = preparedPrompt.stageMetrics(
                            threadLabel = delegate.state.threadPreset.label,
                            batchLabel = delegate.state.batchSizePreset.label,
                            batchThreadLabel = preset.label,
                            prefixReuseEnabled = delegate.state.prefixReuseEnabled
                        )
                    )
                }
                val (stateSnapshotHit, stateSnapshotBytes) = delegate.prepareExplicitStateSnapshot(preparedPrompt.promptPrefixKey)
                val sessionId = delegate.openSmartSession(normalizedQuestion, currentMode)
                val runResult = delegate.runGenerationStep(
                    prompt = preparedPrompt.prompt,
                    maxTokens = currentMode.maxTokens,
                    answerMode = currentMode,
                    sessionId = sessionId,
                    query = normalizedQuestion,
                    benchmarkKind = "prefill_batch_threads",
                    benchmarkGroupId = benchmarkId,
                    benchmarkOrder = index + 1,
                    prefixReuseHit = preparedPrompt.prefixReuseHit,
                    stateSnapshotHit = stateSnapshotHit,
                    stateSnapshotBytes = stateSnapshotBytes
                )
                results += SmartPrefillBenchmarkResult(
                    dimensionLabel = preset.label,
                    status = runResult.status,
                    firstTokenLatencyMs = runResult.firstTokenLatencyMs,
                    answerStartLatencyMs = runResult.answerStartLatencyMs,
                    errorMessage = runResult.errorMessage
                )
            }
            updatePrefillSummary(results, "n_threads_batch 基准", delegate)
        } catch (t: CancellationException) {
            val msg = if (delegate.stopRequested) "n_threads_batch 基准已取消，底层停止流程已接管" else "n_threads_batch 基准已取消。"
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = false, benchmarkTitle = "n_threads_batch 基准", statusText = msg, results = results.toList())) }
        } catch (t: Throwable) {
            delegate.reduceState { copy(prefillBenchmarkState = SmartPrefillBenchmarkUiState(isRunning = false, benchmarkTitle = "n_threads_batch 基准", statusText = "n_threads_batch 基准失败: ${t.message}", results = results.toList())) }
        } finally {
            delegate.applyBatchThreadPresetSynchronously(originalBatchThreadPreset)
        }
    }

    private fun updatePrefillSummary(results: List<SmartPrefillBenchmarkResult>, title: String, delegate: BenchmarkDelegate) {
        delegate.reduceState {
            copy(
                prefillBenchmarkState = SmartPrefillBenchmarkUiState(
                    isRunning = false,
                    benchmarkTitle = title,
                    statusText = buildPrefillBenchmarkSummary(results),
                    results = results.toList()
                )
            )
        }
    }

    fun buildThreadBenchmarkSummary(results: List<SmartThreadBenchmarkResult>): String {
        val completed = results.filter { it.status == "completed" && it.firstTokenLatencyMs != null }
        if (completed.isEmpty()) {
            return if (results.isEmpty()) "线程基准未产出有效结果。"
                   else results.joinToString(" | ") { "${it.threadLabel}:${it.status}${it.errorMessage?.let { e -> "($e)" } ?: ""}" }
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
            return if (results.isEmpty()) "prefill 基准未产出有效结果。"
                   else results.joinToString(" | ") { "${it.dimensionLabel}:${it.status}${it.errorMessage?.let { e -> "($e)" } ?: ""}" }
        }
        val fastest = completed.minByOrNull { it.firstTokenLatencyMs ?: Long.MAX_VALUE }
        val breakdown = results.joinToString(" | ") { result ->
            val latencyText = result.firstTokenLatencyMs?.let { "${it}ms" } ?: result.status
            "${result.dimensionLabel}:$latencyText"
        }
        return if (fastest == null) breakdown else "$breakdown；最快：${fastest.dimensionLabel} = ${fastest.firstTokenLatencyMs}ms"
    }
}
