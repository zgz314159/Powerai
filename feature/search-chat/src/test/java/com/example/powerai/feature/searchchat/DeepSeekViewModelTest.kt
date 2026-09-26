package com.example.powerai.feature.searchchat

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartThreadPreset
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.VectorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

/**
 * Characterization tests for [DeepSeekViewModel].
 *
 * Collaborators are mocked, the main dispatcher is a [StandardTestDispatcher]
 * and every transition is driven by the test scheduler: no sleeps, no real
 * network and no time-sensitive assertions. Covers intent routing, generation
 * delegation with success/error state, abort, benchmark delegation, effect
 * emission and dispatcher/cancellation behaviour.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeepSeekViewModelTest {
    private lateinit var sessionManager: DeepSeekSessionManager
    private lateinit var promptManager: DeepSeekPromptManager
    private lateinit var snapshotHelper: DeepSeekStateSnapshotHelper
    private lateinit var generationOrchestrator: DeepSeekGenerationOrchestrator
    private lateinit var benchmarkOrchestrator: DeepSeekBenchmarkOrchestrator

    @Before
    fun setUp() {
        sessionManager = mock()
        promptManager = mock()
        snapshotHelper = mock()
        generationOrchestrator = mock()
        benchmarkOrchestrator = mock()
        whenever(sessionManager.engineFlow).thenReturn(MutableStateFlow<PowerAIEngine?>(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                body()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private val diagnosticDir: File = Files.createTempDirectory("deepseek-vm-test").toFile()

    private fun createViewModel(generation: DeepSeekGenerationOrchestrator = generationOrchestrator): DeepSeekViewModel {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(diagnosticDir)
        return DeepSeekViewModel(
            context = context,
            vectorRepository = mock<VectorRepository>(),
            knowledgeRepository = mock<KnowledgeRepository>(),
            retrievalFusionUseCase = mock<RetrievalFusionUseCase>(),
            promptManager = promptManager,
            sessionManager = sessionManager,
            snapshotHelper = snapshotHelper,
            generationOrchestrator = generation,
            benchmarkOrchestrator = benchmarkOrchestrator,
        )
    }

    @Test
    fun `load model intent reports success state on the test dispatcher`() {
        runVmTest {
            whenever(sessionManager.loadModel(eq("/models/x.gguf"), eq(true)))
                .thenReturn(Result.success(mock<PowerAIEngine>()))
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.LoadModel("/models/x.gguf", useGpu = true))
            // StandardTestDispatcher: work must not run before the scheduler drains
            assertFalse(vm.uiState.value.isLoading)

            testScheduler.advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.modelLoaded)
            assertEquals("/models/x.gguf", state.modelPath)
            assertEquals("", state.result)
            verify(sessionManager).loadModel("/models/x.gguf", true)
        }
    }

    @Test
    fun `load model failure keeps the stable error text`() {
        runVmTest {
            whenever(sessionManager.loadModel(any(), any()))
                .thenReturn(Result.failure(IllegalStateException("bad")))
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.LoadModel("/models/y.gguf"))
            testScheduler.advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.modelLoaded)
            assertEquals("", state.modelPath)
            assertEquals("加载失败: bad", state.result)
        }
    }

    @Test
    fun `unload model resets path and result`() {
        runVmTest {
            whenever(sessionManager.loadModel(any(), any()))
                .thenReturn(Result.success(mock<PowerAIEngine>()))
            whenever(sessionManager.unloadModel()).thenReturn(Unit)
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.LoadModel("/models/x.gguf"))
            testScheduler.advanceUntilIdle()
            assertTrue(vm.uiState.value.modelLoaded)

            vm.onIntent(DeepSeekIntent.UnloadModel)
            testScheduler.advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.modelLoaded)
            assertEquals("", state.modelPath)
            assertEquals("", state.result)
            verify(sessionManager).unloadModel()
        }
    }

    @Test
    fun `setting intents route to their state fields`() {
        runVmTest {
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.SetAnswerMode(SmartAnswerMode.DEEP))
            assertEquals(SmartAnswerMode.DEEP, vm.uiState.value.answerMode)

            vm.onIntent(DeepSeekIntent.SetBackendMode(SmartBackendMode.VULKAN))
            assertEquals(SmartBackendMode.VULKAN, vm.uiState.value.backendMode)

            vm.onIntent(DeepSeekIntent.SetThreadPreset(SmartThreadPreset.SIX))
            assertEquals(SmartThreadPreset.SIX, vm.uiState.value.threadPreset)

            vm.onIntent(DeepSeekIntent.SetBatchSizePreset(SmartBatchSizePreset.B64))
            assertEquals(SmartBatchSizePreset.B64, vm.uiState.value.batchSizePreset)

            vm.onIntent(DeepSeekIntent.SetBatchThreadPreset(SmartBatchThreadPreset.SIX))
            assertEquals(SmartBatchThreadPreset.SIX, vm.uiState.value.batchThreadPreset)

            vm.onIntent(DeepSeekIntent.SetPrefixReuseEnabled(false))
            assertFalse(vm.uiState.value.prefixReuseEnabled)

            vm.onIntent(DeepSeekIntent.SetStateSnapshotReuseEnabled(true))
            assertTrue(vm.uiState.value.stateSnapshotReuseEnabled)

            testScheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `generate intent delegates to the generation orchestrator with this view model`() {
        runVmTest {
            whenever(sessionManager.startNewRun()).thenReturn("run-1")
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.Generate(prompt = "你好世界", maxTokens = 128))
            testScheduler.advanceUntilIdle()

            val delegateCaptor = argumentCaptor<DeepSeekGenerationOrchestrator.GenerationDelegate>()
            verify(generationOrchestrator).runGeneration(
                delegateCaptor.capture(),
                anyOrNull(),
                any(),
                eq("你好世界"),
                eq(128),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            )
            assertSame(vm, delegateCaptor.firstValue)
            verify(sessionManager).startNewRun()
        }
    }

    @Test
    fun `generation with a missing model surfaces the stable error state`() {
        runVmTest {
            whenever(sessionManager.startNewRun()).thenReturn("run-2")
            val vm = createViewModel(generation = DeepSeekGenerationOrchestrator())

            vm.onIntent(DeepSeekIntent.Generate(prompt = "hello", maxTokens = 32))
            testScheduler.advanceUntilIdle()

            assertEquals("模型未加载。", vm.uiState.value.result)
        }
    }

    @Test
    fun `generation success contract reduces state and completes progress`() {
        runVmTest {
            whenever(sessionManager.startNewRun()).thenReturn("run-3")
            val stub =
                whenever(
                    generationOrchestrator.runGeneration(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyOrNull(),
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        any(),
                        any(),
                    ),
                )
            stub.thenAnswer { invocation ->
                val delegate =
                    invocation.getArgument<DeepSeekGenerationOrchestrator.GenerationDelegate>(0)
                delegate.reduceState { copy(result = "完成的答案") }
                delegate.updateProgress(
                    SmartProgressPhase.COMPLETED,
                    "回答完成",
                    "共接收 1 个片段。",
                    false,
                )
                SmartGenerationRunResult(status = "completed")
            }
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.Generate(prompt = "问题", maxTokens = 64))
            testScheduler.advanceUntilIdle()

            assertEquals("完成的答案", vm.uiState.value.result)
            assertEquals(SmartProgressPhase.COMPLETED, vm.uiState.value.progressState.phase)
            assertEquals("回答完成", vm.uiState.value.progressState.statusText)
            assertFalse(vm.uiState.value.progressState.showAsActive)
        }
    }

    @Test
    fun `abort sets the stop flag and the run guard stays hard wired to true`() {
        runVmTest {
            val vm = createViewModel()

            vm.abortGeneration()
            verify(sessionManager).setStopRequested(true)

            // characterization of the delegate guard: parameter names are swapped
            // versus the interface and the body always returns true
            assertTrue(vm.isRunActive("token-a", "session-b"))
        }
    }

    @Test
    fun `benchmark intents delegate to the benchmark orchestrator`() {
        runVmTest {
            val vm = createViewModel()

            vm.onIntent(DeepSeekIntent.RunThreadLatencyBenchmark("线程问题"))
            testScheduler.advanceUntilIdle()
            vm.onIntent(DeepSeekIntent.RunBatchSizeLatencyBenchmark("批大小问题"))
            testScheduler.advanceUntilIdle()
            vm.onIntent(DeepSeekIntent.RunBatchThreadLatencyBenchmark("批线程问题"))
            testScheduler.advanceUntilIdle()

            verify(benchmarkOrchestrator).runThreadLatencyBenchmark(eq("线程问题"), eq(vm))
            verify(benchmarkOrchestrator).runBatchSizeLatencyBenchmark(eq("批大小问题"), eq(vm))
            verify(benchmarkOrchestrator).runBatchThreadLatencyBenchmark(eq("批线程问题"), eq(vm))
        }
    }

    @Test
    fun `the effect channel never emits for any intent`() {
        runVmTest {
            val vm = createViewModel()
            val emitted = mutableListOf<Any?>()
            backgroundScope.launch { vm.effect.collect { emitted.add(it) } }

            vm.onIntent(DeepSeekIntent.SetAnswerMode(SmartAnswerMode.DEEP))
            vm.onIntent(DeepSeekIntent.SetPrefixReuseEnabled(false))
            vm.onIntent(DeepSeekIntent.UnloadModel)
            testScheduler.advanceUntilIdle()

            assertTrue("effect must stay empty for Effect=Nothing", emitted.isEmpty())
        }
    }

    @Test
    fun `cancelling the view model scope makes further intents inert`() {
        runVmTest {
            whenever(sessionManager.loadModel(any(), any()))
                .thenReturn(Result.success(mock<PowerAIEngine>()))
            val vm = createViewModel()

            vm.viewModelScope.cancel()

            vm.onIntent(DeepSeekIntent.LoadModel("/models/z.gguf"))
            testScheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.modelLoaded)
            verify(sessionManager, never()).loadModel(any(), any())
        }
    }
}
