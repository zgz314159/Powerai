package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.core.model.SmartThreadBenchmarkResult
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartThreadPreset
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.repository.VectorRepository
import com.example.powerai.feature.searchchat.RetrievalFusionUseCase
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * DeepSeek 模块的 ViewModel。
 */
@HiltViewModel
class DeepSeekViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vectorRepository: VectorRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val retrievalFusionUseCase: RetrievalFusionUseCase,
    private val promptManager: DeepSeekPromptManager,
    private val sessionManager: DeepSeekSessionManager,
    private val snapshotHelper: DeepSeekStateSnapshotHelper,
    private val generationOrchestrator: DeepSeekGenerationOrchestrator,
    private val benchmarkOrchestrator: DeepSeekBenchmarkOrchestrator
) : BaseMviViewModel<DeepSeekIntent, DeepSeekUiState, Nothing>(
    initialState = DeepSeekUiState()
), DeepSeekBenchmarkOrchestrator.BenchmarkDelegate, DeepSeekGenerationOrchestrator.GenerationDelegate {

    override val state: DeepSeekUiState get() = currentState
    override val engine: PowerAIEngine? get() = sessionManager.engine
    override val provider: NativeResourceProvider = DeepSeekResourceProvider(context)
    override val CACHE_REUSE_MIN_TOKENS: Int = 64
    override var stopRequested: Boolean
        get() = sessionManager.stopRequested
        set(value) = sessionManager.setStopRequested(value)

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
    }

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            sessionManager.engineFlow
                .flatMapLatest { it?.metrics ?: flowOf(GenerationMetrics()) }
                .collect { metrics ->
                    updateState { copy(generationMetrics = metrics) }
                }
        }
    }

    override fun onIntent(intent: DeepSeekIntent) {
        when (intent) {
            is DeepSeekIntent.LoadModel -> loadDeepSeekFromDevicePath(intent.devicePath, intent.useGpu)
            is DeepSeekIntent.UnloadModel -> unloadDeepSeek()
            is DeepSeekIntent.Generate -> generate(intent.prompt, intent.maxTokens)
            is DeepSeekIntent.GenerateGroundedAnswer -> generateGroundedAnswer(intent.question, intent.maxTokens ?: 160)
            is DeepSeekIntent.SetAnswerMode -> setAnswerMode(intent.mode)
            is DeepSeekIntent.SetBackendMode -> setBackendMode(intent.mode)
            is DeepSeekIntent.SetThreadPreset -> setThreadPreset(intent.preset)
            is DeepSeekIntent.SetBatchSizePreset -> setBatchSizePreset(intent.preset)
            is DeepSeekIntent.SetBatchThreadPreset -> setBatchThreadPreset(intent.preset)
            is DeepSeekIntent.SetPrefixReuseEnabled -> setPrefixReuseEnabled(intent.enabled)
            is DeepSeekIntent.SetStateSnapshotReuseEnabled -> setStateSnapshotReuseEnabled(intent.enabled)
            is DeepSeekIntent.RunStateSnapshotProbe -> runStateSnapshotProbe()
            is DeepSeekIntent.RunThreadLatencyBenchmark -> runThreadLatencyBenchmark(intent.question)
            is DeepSeekIntent.RunBatchSizeLatencyBenchmark -> runBatchSizeLatencyBenchmark(intent.question)
            is DeepSeekIntent.RunBatchThreadLatencyBenchmark -> runBatchThreadLatencyBenchmark(intent.question)
        }
    }

    fun loadDeepSeekFromDevicePath(path: String, useGpu: Boolean) {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            val result = sessionManager.loadModel(path, useGpu)
            updateState {
                copy(
                    isLoading = false,
                    modelLoaded = result.isSuccess,
                    modelPath = if (result.isSuccess) path else "",
                    result = if (result.isFailure) "加载失败: ${result.exceptionOrNull()?.message}" else ""
                )
            }
        }
    }

    fun unloadDeepSeek() {
        viewModelScope.launch {
            sessionManager.unloadModel()
            updateState { copy(modelLoaded = false, modelPath = "", result = "") }
        }
    }

    fun generate(prompt: String, maxTokens: Int) {
        val runToken = sessionManager.startNewRun()
        val sessionId = sessionManager.currentSessionId ?: UUID.randomUUID().toString()
        viewModelScope.launch {
            generationOrchestrator.runGeneration(
                delegate = this@DeepSeekViewModel,
                engine = sessionManager.engine,
                runToken = runToken,
                prompt = prompt,
                maxTokens = maxTokens,
                answerMode = state.answerMode,
                sessionId = sessionId,
                query = prompt.take(50),
                startedAtEpochMs = System.currentTimeMillis()
            )
        }
    }

    private fun generateGroundedAnswer(question: String, maxTokens: Int) {
        val runToken = sessionManager.startNewRun()
        viewModelScope.launch {
            sessionManager.prepareForNextGeneration(state.isLoading)
            val askedAt = System.currentTimeMillis()
            updateState { copy(askedAtMillis = askedAt, result = "", thinking = emptyList()) }

            val prepared = promptManager.prepareGroundedPrompt(
                normalizedQuestion = question,
                answerMode = state.answerMode,
                prefixReuseEnabled = state.prefixReuseEnabled,
                backendLabel = state.backendMode.label,
                provider = provider,
                onProgress = { phase, title, text ->
                    updateProgress(phase, title, text, true)
                }
            )

            val sessionId = sessionManager.openSmartSession(question, state.answerMode, state.backendMode, state.threadPreset)

            generationOrchestrator.runGeneration(
                delegate = this@DeepSeekViewModel,
                engine = sessionManager.engine,
                runToken = runToken,
                prompt = prepared.prompt,
                maxTokens = maxTokens,
                answerMode = state.answerMode,
                sessionId = sessionId,
                query = question,
                startedAtEpochMs = askedAt,
                prefixReuseHit = prepared.prefixReuseHit
            )
        }
    }

    private fun setAnswerMode(mode: SmartAnswerMode) {
        updateState { copy(answerMode = mode) }
    }

    private fun setBackendMode(mode: SmartBackendMode) {
        updateState { copy(backendMode = mode) }
    }

    private fun setThreadPreset(preset: SmartThreadPreset) {
        updateState { copy(threadPreset = preset) }
        viewModelScope.launch { engine?.configureRuntime(preset.threadCount) }
    }

    private fun setBatchSizePreset(preset: SmartBatchSizePreset) {
        updateState { copy(batchSizePreset = preset) }
        viewModelScope.launch { engine?.setOption("n_batch", preset.tokenCount.toString()) }
    }

    private fun setBatchThreadPreset(preset: SmartBatchThreadPreset) {
        updateState { copy(batchThreadPreset = preset) }
        viewModelScope.launch { engine?.setOption("n_threads_batch", preset.threadCount.toString()) }
    }

    private fun setPrefixReuseEnabled(enabled: Boolean) {
        updateState { copy(prefixReuseEnabled = enabled) }
    }

    private fun setStateSnapshotReuseEnabled(enabled: Boolean) {
        updateState { copy(stateSnapshotReuseEnabled = enabled) }
        snapshotHelper.stateSnapshotReuseEnabled = enabled
    }

    private fun runStateSnapshotProbe() {
        viewModelScope.launch {
            val (ok, bytes) = snapshotHelper.runProbe()
            updateProgress(
                SmartProgressPhase.COMPLETED,
                "Snapshot Probe",
                "Result: $ok, Bytes: $bytes",
                false
            )
        }
    }

    private fun runThreadLatencyBenchmark(q: String) {
        viewModelScope.launch {
            benchmarkOrchestrator.runThreadLatencyBenchmark(q, this@DeepSeekViewModel)
        }
    }

    private fun runBatchSizeLatencyBenchmark(q: String) {
        viewModelScope.launch {
            benchmarkOrchestrator.runBatchSizeLatencyBenchmark(q, this@DeepSeekViewModel)
        }
    }

    private fun runBatchThreadLatencyBenchmark(q: String) {
        viewModelScope.launch {
            benchmarkOrchestrator.runBatchThreadLatencyBenchmark(q, this@DeepSeekViewModel)
        }
    }

    // BenchmarkDelegate implementation
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
        stateSnapshotBytes: Int
    ): SmartGenerationRunResult {
        val runToken = sessionManager.startNewRun()
        return generationOrchestrator.runGeneration(
            delegate = this,
            engine = sessionManager.engine,
            runToken = runToken,
            prompt = prompt,
            maxTokens = maxTokens,
            answerMode = answerMode,
            sessionId = sessionId,
            query = query,
            startedAtEpochMs = System.currentTimeMillis(),
            benchmarkKind = benchmarkKind,
            benchmarkGroupId = benchmarkGroupId,
            benchmarkOrder = benchmarkOrder,
            prefixReuseHit = prefixReuseHit,
            stateSnapshotReuseHit = stateSnapshotHit,
            stateSnapshotBytes = stateSnapshotBytes
        )
    }

    override suspend fun prepareForNextGeneration() {
        sessionManager.prepareForNextGeneration(state.isLoading)
    }

    override fun applyThreadPresetSynchronously(preset: SmartThreadPreset) {
        setThreadPreset(preset)
    }

    override fun applyBatchSizePresetSynchronously(preset: SmartBatchSizePreset) {
        setBatchSizePreset(preset)
    }

    override fun applyBatchThreadPresetSynchronously(preset: SmartBatchThreadPreset) {
        setBatchThreadPreset(preset)
    }

    override fun openSmartSession(question: String, mode: SmartAnswerMode): String {
        return sessionManager.openSmartSession(question, mode, state.backendMode, state.threadPreset)
    }

    override suspend fun prepareExplicitStateSnapshot(promptPrefixKey: String): Pair<Boolean, Int> {
        val key = snapshotHelper.buildStateSnapshotKey(
            promptPrefixKey = promptPrefixKey,
            backendMode = state.backendMode,
            threadPreset = state.threadPreset,
            batchSizePreset = state.batchSizePreset,
            batchThreadPreset = state.batchThreadPreset,
            prefixReuseEnabled = state.prefixReuseEnabled
        )
        return snapshotHelper.prepareExplicitStateSnapshot(key)
    }

    override suspend fun prepareGroundedPrompt(question: String, mode: SmartAnswerMode): PreparedGroundedPrompt {
        return promptManager.prepareGroundedPrompt(
            normalizedQuestion = question,
            answerMode = mode,
            prefixReuseEnabled = state.prefixReuseEnabled,
            backendLabel = state.backendMode.label,
            provider = provider,
            onProgress = { phase, title, text ->
                updateProgress(phase, title, text, true)
            }
        )
    }

    override fun reduceState(reducer: DeepSeekUiState.() -> DeepSeekUiState) {
        updateState(reducer)
    }

    override fun updateProgress(phase: SmartProgressPhase, statusText: String, detailText: String, showAsActive: Boolean) {
        updateState {
            copy(
                progressState = SmartProgressUiState(
                    phase = phase,
                    statusText = statusText,
                    detailText = detailText,
                    showAsActive = showAsActive
                )
            )
        }
    }

    fun abortGeneration() {
        sessionManager.setStopRequested(true)
    }

    override fun isRunActive(sessionId: String, runToken: String): Boolean = true
    override fun cachePromptEnabled(): Boolean = true
    override fun isContextOverflowMessage(msg: String): Boolean = false
}
