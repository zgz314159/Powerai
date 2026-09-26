package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartThreadBenchmarkResult
import com.example.powerai.core.model.SmartThreadPreset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Characterization tests for [DeepSeekBenchmarkOrchestrator].
 *
 * All three scenarios are exercised with a scripted delegate and a mocked
 * engine: execution order, state transitions, success/failure summaries,
 * mid-run failure, cancellation and repeat-run reset. No real network or model
 * is touched.
 */
class DeepSeekBenchmarkOrchestratorTest {
    private val question = "断路器越级跳闸如何整定？"

    private fun newOrchestrator(): DeepSeekBenchmarkOrchestrator = DeepSeekBenchmarkOrchestrator(mock())

    private fun loadedDelegate(engine: PowerAIEngine? = null): FakeBenchmarkDelegate =
        FakeBenchmarkDelegate(DeepSeekUiState(modelLoaded = true), engine)

    private suspend fun mockLoadedEngine(): PowerAIEngine {
        val engine = mock<PowerAIEngine>()
        whenever(engine.isLoaded()).thenReturn(true)
        return engine
    }

    private fun threadResult(latencyMs: Long): SmartGenerationRunResult =
        SmartGenerationRunResult(
            status = "completed",
            firstTokenLatencyMs = latencyMs,
            answerStartLatencyMs = latencyMs + 5,
        )

    // ---------------------------------------------------------------- guards

    @Test
    fun `blank question stops the thread benchmark with a stable message`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())

            newOrchestrator().runThreadLatencyBenchmark("   ", delegate)

            val state = delegate.state.threadBenchmarkState
            assertFalse(state.isRunning)
            assertEquals("线程基准需要先输入一个真实问题。", state.statusText)
            assertTrue(delegate.steps.isEmpty())
        }
    }

    @Test
    fun `non cpu backend stops the thread benchmark with a stable message`() {
        runTest {
            val delegate =
                FakeBenchmarkDelegate(
                    DeepSeekUiState(modelLoaded = true, backendMode = SmartBackendMode.VULKAN),
                    mockLoadedEngine(),
                )

            newOrchestrator().runThreadLatencyBenchmark(question, delegate)

            assertEquals("线程基准仅支持 CPU 稳定后端，请先切换到 CPU。", delegate.state.threadBenchmarkState.statusText)
            assertTrue(delegate.steps.isEmpty())
        }
    }

    @Test
    fun `missing model stops the thread benchmark with a stable message`() {
        runTest {
            val unloaded = FakeBenchmarkDelegate(DeepSeekUiState(modelLoaded = false), null)
            newOrchestrator().runThreadLatencyBenchmark(question, unloaded)
            assertEquals("线程基准前请先加载模型。", unloaded.state.threadBenchmarkState.statusText)

            val engineGone = loadedDelegate(null)
            newOrchestrator().runThreadLatencyBenchmark(question, engineGone)
            assertEquals("线程基准前请先加载模型。", engineGone.state.threadBenchmarkState.statusText)
            assertTrue(engineGone.steps.isEmpty())
        }
    }

    @Test
    fun `greeting fast path stops the thread benchmark with a stable message`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())

            newOrchestrator().runThreadLatencyBenchmark("你好", delegate)

            assertEquals(
                "当前问题会命中快捷直答，无法比较模型 first token。请换一个需要真实推理的问题。",
                delegate.state.threadBenchmarkState.statusText,
            )
            assertTrue(delegate.steps.isEmpty())
        }
    }

    @Test
    fun `batch guards use prefill wording`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())

            newOrchestrator().runBatchSizeLatencyBenchmark("", delegate)
            assertEquals("prefill 基准需要先输入一个真实问题。", delegate.state.prefillBenchmarkState.statusText)

            newOrchestrator().runBatchThreadLatencyBenchmark("  ", delegate)
            assertEquals("prefill 基准需要先输入一个真实问题。", delegate.state.prefillBenchmarkState.statusText)
            assertTrue(delegate.steps.isEmpty())
        }
    }

    // ------------------------------------------------------------ happy paths

    @Test
    fun `thread benchmark runs presets in order and transitions state`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += listOf(threadResult(30), threadResult(20), threadResult(10))
            val orchestrator = newOrchestrator()

            orchestrator.runThreadLatencyBenchmark(question, delegate)

            // start-of-run reset is observable in the recorded snapshots
            assertTrue(
                delegate.snapshots.any {
                    it.threadBenchmarkState.isRunning &&
                        it.threadBenchmarkState.results.isEmpty() &&
                        it.threadBenchmarkState.statusText == "正在准备 4/6/8 线程基准"
                },
            )

            // sequential preset application, original preset restored last
            assertEquals(
                listOf(
                    SmartThreadPreset.FOUR,
                    SmartThreadPreset.SIX,
                    SmartThreadPreset.EIGHT,
                    SmartThreadPreset.FOUR,
                ),
                delegate.threadPresetsApplied,
            )

            // one generation step per preset with the thread scenario contract
            assertEquals(listOf(1, 2, 3), delegate.steps.map { it.order })
            assertTrue(delegate.steps.all { it.kind == "thread_latency" })
            assertEquals(SmartAnswerMode.FAST.maxTokens, delegate.steps.first().maxTokens)
            assertTrue(delegate.sessionIds.isNotEmpty())

            val finalState = delegate.state.threadBenchmarkState
            assertFalse(finalState.isRunning)
            assertEquals(listOf(30L, 20L, 10L), finalState.results.map { it.firstTokenLatencyMs })
            val expectedSummary =
                "${SmartThreadPreset.FOUR.label}:30ms | " +
                    "${SmartThreadPreset.SIX.label}:20ms | " +
                    "${SmartThreadPreset.EIGHT.label}:10ms；最快：" +
                    "${SmartThreadPreset.EIGHT.label} = 10ms"
            assertEquals(expectedSummary, finalState.statusText)

            val completed = delegate.progress.last()
            assertEquals(SmartProgressPhase.COMPLETED, completed.phase)
            assertEquals("线程基准完成", completed.statusText)
            assertEquals(expectedSummary, completed.detailText)
            assertFalse(completed.showAsActive)

            assertNotNull(delegate.state.askedAtMillis)
            assertEquals(delegate.references, delegate.state.smartReferences)
            // per-step progress snapshot carries the (1/3) format
            assertTrue(delegate.snapshots.any { it.threadBenchmarkState.statusText.contains("（1/3）") })
        }
    }

    @Test
    fun `batch size benchmark runs four presets with its own scenario kind`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += listOf(threadResult(40), threadResult(30), threadResult(20), threadResult(10))
            val orchestrator = newOrchestrator()

            orchestrator.runBatchSizeLatencyBenchmark(question, delegate)

            assertEquals(
                listOf(
                    SmartBatchSizePreset.B32,
                    SmartBatchSizePreset.B64,
                    SmartBatchSizePreset.B128,
                    SmartBatchSizePreset.B256,
                    SmartBatchSizePreset.B128,
                ),
                delegate.batchSizePresetsApplied,
            )
            assertTrue(delegate.steps.all { it.kind == "prefill_batch_size" })
            assertEquals(listOf(1, 2, 3, 4), delegate.steps.map { it.order })

            val finalState = delegate.state.prefillBenchmarkState
            assertFalse(finalState.isRunning)
            assertEquals("n_batch 基准", finalState.benchmarkTitle)
            assertEquals(4, finalState.results.size)
            val expectedSummary =
                "${SmartBatchSizePreset.B32.label}:40ms | " +
                    "${SmartBatchSizePreset.B64.label}:30ms | " +
                    "${SmartBatchSizePreset.B128.label}:20ms | " +
                    "${SmartBatchSizePreset.B256.label}:10ms；最快：" +
                    "${SmartBatchSizePreset.B256.label} = 10ms"
            assertEquals(expectedSummary, finalState.statusText)
            assertTrue(delegate.snapshots.any { it.prefillBenchmarkState.statusText == "正在准备 n_batch 基准" })
        }
    }

    @Test
    fun `batch thread benchmark uses its own presets and scenario kind`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += listOf(threadResult(4), threadResult(3), threadResult(2), threadResult(1))
            val orchestrator = newOrchestrator()

            orchestrator.runBatchThreadLatencyBenchmark(question, delegate)

            assertEquals(
                listOf(
                    SmartBatchThreadPreset.TWO,
                    SmartBatchThreadPreset.FOUR,
                    SmartBatchThreadPreset.SIX,
                    SmartBatchThreadPreset.EIGHT,
                    SmartBatchThreadPreset.FOUR,
                ),
                delegate.batchThreadPresetsApplied,
            )
            assertTrue(delegate.steps.all { it.kind == "prefill_batch_threads" })
            assertEquals("n_threads_batch 基准", delegate.state.prefillBenchmarkState.benchmarkTitle)
            assertFalse(delegate.state.prefillBenchmarkState.isRunning)
            assertEquals(4, delegate.state.prefillBenchmarkState.results.size)
        }
    }

    // ------------------------------------------------------- failure and cancel

    @Test
    fun `mid run failure keeps partial results and restores the preset`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += threadResult(30)
            delegate.failAtCall = 2
            val orchestrator = newOrchestrator()

            orchestrator.runThreadLatencyBenchmark(question, delegate)

            val state = delegate.state.threadBenchmarkState
            assertFalse(state.isRunning)
            assertEquals("线程基准失败: boom", state.statusText)
            assertEquals(1, state.results.size)
            // original preset restored even on failure
            assertEquals(
                listOf(SmartThreadPreset.FOUR, SmartThreadPreset.SIX, SmartThreadPreset.FOUR),
                delegate.threadPresetsApplied,
            )
            assertTrue(delegate.progress.none { it.phase == SmartProgressPhase.COMPLETED })
        }
    }

    @Test
    fun `cancellation reports the stop requested takeover message`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += threadResult(30)
            delegate.hangAtCall = 2
            delegate.stopRequested = true
            val orchestrator = newOrchestrator()

            val job = launch { orchestrator.runThreadLatencyBenchmark(question, delegate) }
            testScheduler.advanceUntilIdle()
            assertEquals(2, delegate.steps.size)
            job.cancel()
            testScheduler.advanceUntilIdle()
            job.join()

            val state = delegate.state.threadBenchmarkState
            assertFalse(state.isRunning)
            assertEquals("线程基准已取消，底层停止流程已接管", state.statusText)
            assertEquals(1, state.results.size)
        }
    }

    @Test
    fun `cancellation without stop request reports the generic cancel message`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += threadResult(30)
            delegate.hangAtCall = 2
            delegate.stopRequested = false
            val orchestrator = newOrchestrator()

            val job = launch { orchestrator.runThreadLatencyBenchmark(question, delegate) }
            testScheduler.advanceUntilIdle()
            job.cancel()
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals("线程基准已取消。", delegate.state.threadBenchmarkState.statusText)
            assertFalse(delegate.state.threadBenchmarkState.isRunning)
        }
    }

    @Test
    fun `repeat runs reset the benchmark state before collecting new results`() {
        runTest {
            val delegate = loadedDelegate(mockLoadedEngine())
            delegate.stepResults += listOf(threadResult(30), threadResult(20), threadResult(10))
            val orchestrator = newOrchestrator()

            orchestrator.runThreadLatencyBenchmark(question, delegate)
            assertEquals(3, delegate.state.threadBenchmarkState.results.size)
            val snapshotAfterFirstRun = delegate.snapshots.size

            delegate.stepResults.clear()
            delegate.stepResults += listOf(threadResult(7), threadResult(6), threadResult(5))
            orchestrator.runThreadLatencyBenchmark(question, delegate)

            // after the first run finished, a reset snapshot (running + empty
            // results) must exist for the second run
            assertTrue(
                delegate.snapshots.drop(snapshotAfterFirstRun).any {
                    it.threadBenchmarkState.isRunning && it.threadBenchmarkState.results.isEmpty()
                },
            )
            val finalState = delegate.state.threadBenchmarkState
            assertFalse(finalState.isRunning)
            assertEquals(listOf(7L, 6L, 5L), finalState.results.map { it.firstTokenLatencyMs })
            assertEquals(2, delegate.runCount)
        }
    }

    // -------------------------------------------------------- summary builders

    @Test
    fun `thread summary builder formats empty failed and fastest results`() {
        val orchestrator = newOrchestrator()
        assertEquals("线程基准未产出有效结果。", orchestrator.buildThreadBenchmarkSummary(emptyList()))

        val failed =
            SmartThreadBenchmarkResult(
                threadLabel = SmartThreadPreset.FOUR.label,
                status = "error",
                errorMessage = "boom",
            )
        assertEquals(
            "${SmartThreadPreset.FOUR.label}:error(boom)",
            orchestrator.buildThreadBenchmarkSummary(listOf(failed)),
        )

        val ok =
            SmartThreadBenchmarkResult(
                threadLabel = SmartThreadPreset.FOUR.label,
                status = "completed",
                firstTokenLatencyMs = 50,
            )
        // mixed completed/failed: the breakdown shows status without the error text
        assertEquals(
            "${SmartThreadPreset.FOUR.label}:50ms | ${SmartThreadPreset.FOUR.label}:error；最快：" +
                "${SmartThreadPreset.FOUR.label} = 50ms",
            orchestrator.buildThreadBenchmarkSummary(listOf(ok, failed)),
        )
    }

    @Test
    fun `prefill summary builder formats empty and fastest results`() {
        val orchestrator = newOrchestrator()
        assertEquals("prefill 基准未产出有效结果。", orchestrator.buildPrefillBenchmarkSummary(emptyList()))

        val ok =
            SmartPrefillBenchmarkResult(
                dimensionLabel = SmartBatchSizePreset.B32.label,
                status = "completed",
                firstTokenLatencyMs = 9,
            )
        assertEquals(
            "${SmartBatchSizePreset.B32.label}:9ms；最快：${SmartBatchSizePreset.B32.label} = 9ms",
            orchestrator.buildPrefillBenchmarkSummary(listOf(ok)),
        )
    }
}

/** Records every delegate interaction so scenarios can be asserted precisely. */
private class FakeBenchmarkDelegate(
    initial: DeepSeekUiState,
    override val engine: PowerAIEngine?,
) : DeepSeekBenchmarkOrchestrator.BenchmarkDelegate {
    private var currentState: DeepSeekUiState = initial

    override val state: DeepSeekUiState get() = currentState

    override val provider: NativeResourceProvider = mock<NativeResourceProvider>()

    @Suppress("PropertyName")
    override val CACHE_REUSE_MIN_TOKENS: Int = 64

    override var stopRequested: Boolean = false

    val references: List<KnowledgeItem> =
        listOf(
            KnowledgeItem(
                id = 1L,
                title = "参考1",
                content = "content",
                source = "src.pdf",
                category = "分类",
                keywords = emptyList(),
            ),
        )

    val snapshots = mutableListOf<DeepSeekUiState>()
    val progress = mutableListOf<SmartProgressUiState>()
    val steps = mutableListOf<StepCall>()
    val sessionIds = mutableListOf<String>()
    val threadPresetsApplied = mutableListOf<SmartThreadPreset>()
    val batchSizePresetsApplied = mutableListOf<SmartBatchSizePreset>()
    val batchThreadPresetsApplied = mutableListOf<SmartBatchThreadPreset>()
    val stepResults = mutableListOf<SmartGenerationRunResult>()

    var failAtCall: Int? = null
    var hangAtCall: Int? = null
    var runCount: Int = 0
        private set

    data class StepCall(
        val kind: String,
        val order: Int,
        val sessionId: String,
        val maxTokens: Int,
    )

    override fun reduceState(reducer: DeepSeekUiState.() -> DeepSeekUiState) {
        currentState = currentState.reducer()
        snapshots += currentState
    }

    override fun updateProgress(
        phase: SmartProgressPhase,
        statusText: String,
        detailText: String,
        showAsActive: Boolean,
    ) {
        progress +=
            SmartProgressUiState(
                phase = phase,
                statusText = statusText,
                detailText = detailText,
                showAsActive = showAsActive,
            )
    }

    override suspend fun runGenerationStep(
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
        stateSnapshotBytes: Int,
    ): SmartGenerationRunResult {
        val call = benchmarkOrder
        steps += StepCall(benchmarkKind, call, sessionId, maxTokens)
        sessionIds += sessionId
        if (hangAtCall == call) {
            CompletableDeferred<SmartGenerationRunResult>().await()
        }
        if (failAtCall == call) {
            throw IllegalStateException("boom")
        }
        runCount += if (benchmarkOrder == 1) 1 else 0
        return stepResults.removeFirstOrNull()
            ?: SmartGenerationRunResult(status = "completed", firstTokenLatencyMs = 1L)
    }

    override suspend fun prepareForNextGeneration() {
        // no-op: only ordering matters for the orchestrator
    }

    override fun applyThreadPresetSynchronously(preset: SmartThreadPreset) {
        threadPresetsApplied += preset
        reduceState { copy(threadPreset = preset) }
    }

    override fun applyBatchSizePresetSynchronously(preset: SmartBatchSizePreset) {
        batchSizePresetsApplied += preset
        reduceState { copy(batchSizePreset = preset) }
    }

    override fun applyBatchThreadPresetSynchronously(preset: SmartBatchThreadPreset) {
        batchThreadPresetsApplied += preset
        reduceState { copy(batchThreadPreset = preset) }
    }

    override fun openSmartSession(
        question: String,
        mode: SmartAnswerMode,
    ): String {
        val id = "session-${sessionIds.size}"
        sessionIds += id
        return id
    }

    override suspend fun prepareExplicitStateSnapshot(promptPrefixKey: String): Pair<Boolean, Int> = false to 0

    override suspend fun prepareGroundedPrompt(
        question: String,
        mode: SmartAnswerMode,
    ): PreparedGroundedPrompt =
        PreparedGroundedPrompt(
            question = question,
            prompt = "prompt-for-$question",
            promptPrefixKey = "prefix-key",
            references = references,
            retrievalMs = 1L,
            promptBuildMs = 2L,
            promptChars = 12,
            prefixReuseHit = false,
            answerMode = mode,
            backendLabel = "CPU",
        )

    override fun cachePromptEnabled(): Boolean = true
}
