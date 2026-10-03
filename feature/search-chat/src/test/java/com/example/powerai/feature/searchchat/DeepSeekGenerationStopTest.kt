package com.example.powerai.feature.searchchat

import android.content.Context
import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.VectorRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 停止闭环的端到端回归：真实 [DeepSeekViewModel] + 真实 [DeepSeekGenerationOrchestrator]
 * + 真实 [DeepSeekSessionManager] + 可控 fake [PowerAIEngine]。
 *
 * 覆盖：停止前正常流、停止后迟到 chunk/最终结果被拒绝、连续点击停止、停止后立即新提问、
 * 旧会话迟到回调不污染新会话、真正引擎异常与用户主动停止显示不同状态，
 * 以及驱动「停止生成」按钮显隐的 UI 状态谓词。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeepSeekGenerationStopTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runStopTest(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            body()
        }

    private class Harness(
        val engine: FakePowerAIEngine,
        val sessionManager: DeepSeekSessionManager,
        val vm: DeepSeekViewModel,
    )

    private suspend fun TestScope.buildHarness(): Harness {
        val dir: File = Files.createTempDirectory("deepseek-stop-test").toFile()
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(dir)

        val engine = FakePowerAIEngine()
        val sessionManager =
            spy(
                DeepSeekSessionManager(
                    context = context,
                    nativeResourceProvider = mock<NativeResourceProvider>(),
                    vectorRepository = mock<VectorRepository>(),
                    knowledgeRepository = mock<KnowledgeRepository>(),
                ),
            )
        whenever(sessionManager.engine).thenReturn(engine)

        val promptManager = mock<DeepSeekPromptManager>()
        whenever(
            promptManager.prepareGroundedPrompt(any(), any(), any(), any(), any(), any()),
        ).thenReturn(
            PreparedGroundedPrompt(
                question = "问题",
                prompt = "prompt",
                promptPrefixKey = "key",
                references = emptyList(),
                retrievalMs = 0L,
                promptBuildMs = 0L,
                promptChars = 6,
                prefixReuseHit = false,
                answerMode = SmartAnswerMode.FAST,
                backendLabel = "CPU",
            ),
        )

        val vm =
            DeepSeekViewModel(
                context = context,
                vectorRepository = mock<VectorRepository>(),
                knowledgeRepository = mock<KnowledgeRepository>(),
                retrievalFusionUseCase = mock<RetrievalFusionUseCase>(),
                promptManager = promptManager,
                sessionManager = sessionManager,
                snapshotHelper = mock<DeepSeekStateSnapshotHelper>(),
                generationOrchestrator = DeepSeekGenerationOrchestrator(),
                benchmarkOrchestrator = mock<DeepSeekBenchmarkOrchestrator>(),
            )
        return Harness(engine, sessionManager, vm)
    }

    private fun TestScope.startGeneration(
        h: Harness,
        question: String = "问题",
    ) {
        h.vm.onIntent(DeepSeekIntent.GenerateGroundedAnswer(question, 64))
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `the stop affordance is exposed only while a generation is running`() =
        runStopTest {
            val h = buildHarness()
            assertFalse(h.vm.uiState.value.canAbortGeneration)

            startGeneration(h)
            h.engine.awaitInferStarted()
            assertTrue("停止按钮应在生成进行中可见", h.vm.uiState.value.canAbortGeneration)

            h.vm.abortGeneration()
            testScheduler.advanceUntilIdle()
            assertFalse("停止后不应再显示停止按钮", h.vm.uiState.value.canAbortGeneration)
        }

    @Test
    fun `stop cancels the running generation and rejects late chunk and final result`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h)
            h.engine.awaitInferStarted()

            h.engine.emitChunk("第一段答案")
            testScheduler.advanceUntilIdle()
            assertEquals("第一段答案", h.vm.uiState.value.result)

            val runningJob: Job? = h.sessionManager.generationJob
            h.vm.abortGeneration()
            testScheduler.advanceUntilIdle()
            runningJob?.join()
            testScheduler.advanceUntilIdle()

            assertTrue("底层引擎必须收到停止调用", h.engine.stopCalls.get() >= 1)
            assertEquals(SmartProgressPhase.CANCELLED, h.vm.uiState.value.progressState.phase)
            assertEquals("第一段答案", h.vm.uiState.value.result)

            h.engine.emitChunk("迟到片段")
            testScheduler.advanceUntilIdle()
            assertEquals("迟到 chunk 不得写回界面", "第一段答案", h.vm.uiState.value.result)

            h.engine.finishInferAt(0, "迟到的最终答案")
            testScheduler.advanceUntilIdle()
            assertEquals("迟到最终结果不得写回界面", "第一段答案", h.vm.uiState.value.result)
            assertNotEquals(SmartProgressPhase.ERROR, h.vm.uiState.value.progressState.phase)
            assertFalse(h.vm.uiState.value.canAbortGeneration)
        }

    @Test
    fun `a stale run cannot write its final result after the stop flag is set`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h)
            h.engine.awaitInferStarted()

            // 用户已请求停止（不取消 job），验证迟到最终结果被运行守卫拒绝
            h.sessionManager.setStopRequested(true)
            val job: Job? = h.sessionManager.generationJob
            h.engine.finishInferAt(0, "迟到的最终答案")
            job?.join()
            testScheduler.advanceUntilIdle()

            assertNotEquals("迟到的最终答案", h.vm.uiState.value.result)
            assertEquals("", h.vm.uiState.value.result)
        }

    @Test
    fun `stopping repeatedly stays safe and stopped`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h)
            h.engine.awaitInferStarted()
            val runningJob: Job? = h.sessionManager.generationJob

            h.vm.abortGeneration()
            h.vm.abortGeneration()
            testScheduler.advanceUntilIdle()
            runningJob?.join()
            testScheduler.advanceUntilIdle()

            assertTrue(h.engine.stopCalls.get() >= 1)
            assertEquals(SmartProgressPhase.CANCELLED, h.vm.uiState.value.progressState.phase)
            assertFalse(h.vm.uiState.value.canAbortGeneration)
        }

    @Test
    fun `a new question after stop starts a fresh run`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h, "第一问")
            h.engine.awaitInferStarted()
            h.engine.emitChunk("旧答案")
            testScheduler.advanceUntilIdle()
            assertEquals("旧答案", h.vm.uiState.value.result)

            h.vm.abortGeneration()
            testScheduler.advanceUntilIdle()
            assertEquals(SmartProgressPhase.CANCELLED, h.vm.uiState.value.progressState.phase)

            startGeneration(h, "第二问")
            h.engine.awaitInferStarted()
            assertTrue("新提问应重新进入可停止的生成中", h.vm.uiState.value.canAbortGeneration)

            h.engine.emitChunk("新答案")
            testScheduler.advanceUntilIdle()
            assertEquals("新答案", h.vm.uiState.value.result)

            val secondJob: Job? = h.sessionManager.generationJob
            h.engine.finishInferAt(1, "新答案")
            secondJob?.join()
            testScheduler.advanceUntilIdle()
            assertEquals(SmartProgressPhase.COMPLETED, h.vm.uiState.value.progressState.phase)
            assertEquals("新答案", h.vm.uiState.value.result)
        }

    @Test
    fun `a stale session's late final result cannot pollute the new session`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h, "旧会话")
            h.engine.awaitInferStarted()
            h.engine.emitChunk("旧答案")
            testScheduler.advanceUntilIdle()
            val staleJob: Job? = h.sessionManager.generationJob

            // 直接发起新提问抢占旧运行（不走停止，旧 job 仍存活）
            startGeneration(h, "新会话")
            h.engine.awaitInferStarted()

            // 旧运行的迟到最终结果必须被守卫拒绝
            h.engine.finishInferAt(0, "旧会话的迟到最终答案")
            staleJob?.join()
            testScheduler.advanceUntilIdle()
            assertNotEquals("旧会话的迟到最终答案", h.vm.uiState.value.result)

            h.engine.emitChunk("新会话答案")
            testScheduler.advanceUntilIdle()
            assertEquals("新会话答案", h.vm.uiState.value.result)
        }

    @Test
    fun `a real engine failure surfaces the error state`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h)
            h.engine.awaitInferStarted()
            val job: Job? = h.sessionManager.generationJob

            h.engine.finishInferAtWithError(0, IllegalStateException("boom"))
            job?.join()
            testScheduler.advanceUntilIdle()

            assertEquals(SmartProgressPhase.ERROR, h.vm.uiState.value.progressState.phase)
            assertTrue(h.vm.uiState.value.result.contains("推理失败"))
        }

    @Test
    fun `a user stop is not surfaced as an engine error`() =
        runStopTest {
            val h = buildHarness()
            startGeneration(h)
            h.engine.awaitInferStarted()
            val job: Job? = h.sessionManager.generationJob

            // 用户已请求停止；随后底层抛出真实异常也不得被归类为「推理失败」
            h.sessionManager.setStopRequested(true)
            h.engine.finishInferAtWithError(0, IllegalStateException("boom"))
            job?.join()
            testScheduler.advanceUntilIdle()

            assertNotEquals(SmartProgressPhase.ERROR, h.vm.uiState.value.progressState.phase)
            assertFalse(h.vm.uiState.value.result.contains("推理失败"))
        }

    /**
     * 可控 fake 引擎：每个 infer 在独立 gate 上挂起，由测试按索引决定何时返回/抛错；
     * chunk 通过 [emitChunk] 主动投递，[stopGeneration] 记录调用并清掉 isGenerating。
     */
    private class FakePowerAIEngine : PowerAIEngine {
        private val chunks = MutableSharedFlow<String>(extraBufferCapacity = 64)
        private val metricsFlow = MutableStateFlow(GenerationMetrics())
        private val started = Channel<Unit>(Channel.UNLIMITED)
        private val gates = CopyOnWriteArrayList<CompletableDeferred<String>>()

        val stopCalls = AtomicInteger(0)

        @Volatile
        private var errorOnFinish: Throwable? = null

        override val chunkFlow: SharedFlow<String> = chunks

        override val thinkingFlow: SharedFlow<String>? = null

        override val metrics: StateFlow<GenerationMetrics> = metricsFlow

        fun emitChunk(text: String) {
            chunks.tryEmit(text)
        }

        suspend fun awaitInferStarted() {
            started.receive()
        }

        fun finishInferAt(
            index: Int,
            text: String,
        ) {
            gates.getOrNull(index)?.complete(text)
        }

        fun finishInferAtWithError(
            index: Int,
            error: Throwable,
        ) {
            errorOnFinish = error
            gates.getOrNull(index)?.complete("ignored")
        }

        override suspend fun infer(
            prompt: String,
            maxTokens: Int,
            stream: Boolean,
        ): String {
            metricsFlow.value = GenerationMetrics(isGenerating = true)
            val gate = CompletableDeferred<String>()
            gates.add(gate)
            started.trySend(Unit)
            try {
                val text = gate.await()
                errorOnFinish?.let { throw it }
                return text
            } finally {
                metricsFlow.value = metricsFlow.value.copy(isGenerating = false)
            }
        }

        override suspend fun stopGeneration(): Boolean {
            stopCalls.incrementAndGet()
            metricsFlow.value = metricsFlow.value.copy(isGenerating = false)
            return true
        }

        override suspend fun isLoaded(): Boolean = true

        override suspend fun init(provider: NativeResourceProvider) {}

        override suspend fun loadModel(
            modelPath: String,
            useGpu: Boolean,
        ): Boolean = true

        override suspend fun unload() {}

        override suspend fun setSystemPrompt(prompt: String): Boolean = true

        override suspend fun configureStreamingThinking(enabled: Boolean) {}

        override suspend fun configureRuntime(
            threadCount: Int,
            contextLength: Int,
        ) {}

        override suspend fun setOption(
            key: String,
            value: String,
        ): Boolean = true

        override suspend fun getStateData(): ByteArray? = null

        override suspend fun loadStateData(data: ByteArray?): Boolean = true
    }
}
