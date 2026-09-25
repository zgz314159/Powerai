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
import com.example.powerai.core.repository.KnowledgeRepository

import android.content.Context
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.engine.ai.PowerAIEngineFactory
import com.example.powerai.engine.ai.PowerAIEngineManager
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import com.example.powerai.core.repository.VectorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@ViewModelScoped
class DeepSeekSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeResourceProvider: NativeResourceProvider,
    private val vectorRepository: VectorRepository,
    private val knowledgeRepository: KnowledgeRepository
) {
    private val _engine = MutableStateFlow<PowerAIEngine?>(null)
    val engineFlow: StateFlow<PowerAIEngine?> = _engine.asStateFlow()

    var engine: PowerAIEngine?
        get() = _engine.value
        private set(value) { _engine.value = value }

    var currentSessionId: String? = null
        private set

    @Volatile
    var activeRunToken: String? = null
        private set

    @Volatile
    var stopRequested: Boolean = false
        private set

    var answerCollectorJob: Job? = null
    var generationJob: Job? = null
    var stopDrainJob: Job? = null

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val STREAM_DRAIN_QUIET_MS = 800L
        const val STREAM_DRAIN_TIMEOUT_MS = 2_500L
    }

    suspend fun loadModel(devicePath: String, useGpu: Boolean): Result<PowerAIEngine> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val eng = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    runCatching {
                        PowerAIEngineManager.switchTo(
                            PowerAIEngineFactory.EngineType.DEEPSEEK,
                            nativeResourceProvider,
                            vectorRepository,
                            knowledgeRepository,
                            devicePath,
                            useGpu
                        )
                    }.getOrElse {
                        if (!useGpu) throw it
                        PowerAIEngineManager.switchTo(
                            PowerAIEngineFactory.EngineType.DEEPSEEK,
                            nativeResourceProvider,
                            vectorRepository,
                            knowledgeRepository,
                            devicePath,
                            false
                        )
                    }
                } ?: throw IllegalStateException("加载超时")
                engine = eng
                eng
            }
        }
    }

    suspend fun unloadModel() {
        stopRequested = true
        invalidateActiveRun()
        engine?.stopGeneration()
        generationJob?.cancel()
        generationJob = null
        try {
            PowerAIEngineManager.closeActive()
        } catch (_: Throwable) {}
        answerCollectorJob?.cancel()
        answerCollectorJob = null
        engine = null
    }

    fun openSmartSession(query: String, answerMode: SmartAnswerMode, backendMode: SmartBackendMode, threadPreset: SmartThreadPreset): String {
        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        SmartDeepSeekDebugLogger.resetSession(
            provider = nativeResourceProvider,
            sessionId = sessionId,
            query = query,
            answerMode = answerMode.label,
            backendMode = backendMode.label,
            threadMode = threadPreset.label
        )
        return sessionId
    }

    fun invalidateActiveRun() {
        activeRunToken = null
        currentSessionId = null
    }

    fun isRunActive(runToken: String, sessionId: String): Boolean {
        return !stopRequested && activeRunToken == runToken && currentSessionId == sessionId
    }

    fun startNewRun(): String {
        val token = UUID.randomUUID().toString()
        activeRunToken = token
        return token
    }

    fun setStopRequested(requested: Boolean) {
        stopRequested = requested
    }

    suspend fun prepareForNextGeneration(isLoading: Boolean) {
        val eng = engine
        val isGenerating = eng?.metrics?.value?.isGenerating ?: false
        val hadInFlight = isLoading || answerCollectorJob != null || isGenerating
        stopRequested = true
        stopDrainJob?.cancel()
        stopDrainJob = null
        invalidateActiveRun()
        answerCollectorJob?.cancel()
        answerCollectorJob = null
        if (hadInFlight) {
            SmartDeepSeekDebugLogger.logEvent(
                provider = nativeResourceProvider,
                tag = "STOP_PREPARE",
                message = "hadInFlight=true snapshot=${drainSnapshot(isLoading)}"
            )
            val stopAccepted = eng?.stopGeneration() ?: false
            SmartDeepSeekDebugLogger.logEvent(
                provider = nativeResourceProvider,
                tag = "STOP_PREPARE",
                message = "stopGeneration accepted=$stopAccepted snapshot=${drainSnapshot(isLoading)}"
            )
            waitForNativeDrain(isLoading)
        }
        stopRequested = false
    }

    suspend fun waitForNativeDrain(isLoading: Boolean) {
        val eng = engine ?: return
        val startedAtMs = System.currentTimeMillis()
        var lastReportAtMs = 0L
        val drainReason = withTimeoutOrNull(STREAM_DRAIN_TIMEOUT_MS) {
            while (true) {
                val now = System.currentTimeMillis()
                val metrics = eng.metrics.value
                val finished = !metrics.isGenerating
                // Note: lastChunkAtMs is not explicitly in metrics yet, but we can rely on isGenerating
                // or just wait for finished
                if (finished) {
                    return@withTimeoutOrNull "finished"
                }
                if (now - lastReportAtMs >= 250L) {
                    lastReportAtMs = now
                    SmartDeepSeekDebugLogger.logEvent(
                        provider = nativeResourceProvider,
                        tag = "STOP_DRAIN",
                        message = "poll elapsedMs=${now - startedAtMs} snapshot=${drainSnapshot(isLoading, now)}"
                    )
                }
                delay(50)
            }
        }
        val endedAtMs = System.currentTimeMillis()
        SmartDeepSeekDebugLogger.logEvent(
            provider = nativeResourceProvider,
            tag = "STOP_DRAIN",
            message = "end reason=${drainReason ?: "timeout"} elapsedMs=${endedAtMs - startedAtMs} snapshot=${drainSnapshot(isLoading, endedAtMs)}"
        )
    }

    fun drainSnapshot(isLoading: Boolean, now: Long = System.currentTimeMillis()): String {
        val eng = engine
        val metrics = eng?.metrics?.value
        return "isLoading=$isLoading stopRequested=$stopRequested isGenerating=${metrics?.isGenerating} streamed=${metrics?.streamedTokenCount}"
    }
}
