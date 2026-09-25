package com.example.powerai.engine.ai

import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import android.util.Log
import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * LlamaJni - Refactored facade for native model interaction.
 * Logic is now distributed across specialized bridge classes.
 */
object LlamaJni {
    const val DEFAULT_CONTEXT_LENGTH = 1024
    const val DEFAULT_RECOMMENDED_MAX_TOKENS = 768
    private const val LOAD_CALL_TIMEOUT_MS = 20_000L
    private const val READY_CALL_TIMEOUT_MS = 3_000L
    private const val DEFAULT_INIT_TEMPERATURE = 0.7f
    private const val DEFAULT_INIT_TOP_K = 20
    private const val DEFAULT_INIT_TOP_P = 0.9f
    private const val DEFAULT_INIT_MIN_P = 0.0f

    private const val TAG = "llama_jni"

    private val aarExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val stopExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val generationMetrics: StateFlow<GenerationMetrics> get() = LlamaMetricsManager.generationMetrics
    val finishSequence: StateFlow<Long> get() = LlamaMetricsManager.finishSequence
    val lastChunkAtMs: StateFlow<Long> get() = LlamaMetricsManager.lastChunkAtMs

    @Volatile
    var resourceProvider: NativeResourceProvider? = null

    @Volatile
    var appContext: android.content.Context? = null

    @Volatile
    private var configuredThreadCount: Int = 2
    @Volatile
    private var configuredContextLength: Int = DEFAULT_CONTEXT_LENGTH

    @Volatile
    private var stopRequestSerial: Long = 0L
    @Volatile
    private var lastAppliedSystemPrompt: String? = null
    @Volatile
    private var modelLoaded: Boolean = false

    fun initBackend(backend: String?): Boolean = LlamaNativeBridge.initBackend(backend)
    fun loadModel(path: String?): Boolean = LlamaNativeBridge.loadModel(path)
    fun unloadModel(): Boolean = LlamaNativeBridge.unloadModel()
    fun generate(prompt: String?, maxTokens: Int): String? = LlamaNativeBridge.generate(prompt, maxTokens)
    fun setOption(key: String?, value: String?): Boolean = LlamaNativeBridge.setOption(key, value)
    fun nativeStopGeneration(): Unit = LlamaNativeBridge.nativeStopGeneration()
    fun generateAsync(prompt: String?, maxTokens: Int): Boolean = LlamaNativeBridge.generateAsync(prompt, maxTokens)
    fun nativeGenerateStream(prompt: String?): Boolean = LlamaNativeBridge.nativeGenerateStream(prompt)

    fun provideResources(provider: NativeResourceProvider, context: android.content.Context? = null) {
        resourceProvider = provider
        appContext = context ?: provider.getContext() as? android.content.Context
    }

    fun configureRuntime(threadCount: Int, contextLength: Int = DEFAULT_CONTEXT_LENGTH) {
        configuredThreadCount = threadCount.coerceAtLeast(1)
        configuredContextLength = contextLength.coerceAtLeast(256)
    }

    fun configureStreamingThinking(enabled: Boolean) {
        LlamaJniStreaming.streamingThinkingEnabled = enabled
        LlamaJniStreaming.resetStreamingFilters()
    }

    fun safeInitBackend(backend: String?): Boolean {
        return LlamaNativeBridge.initBackend(backend)
    }

    fun safeLoadModel(path: String?): Boolean {
        val provider = resourceProvider ?: return false
        return LlamaEngineController.loadModel(path, provider, appContext)
    }

    fun safeUnload(): Boolean {
        lastAppliedSystemPrompt = null
        modelLoaded = false
        return LlamaEngineController.unloadModel()
    }

    fun safeStopGeneration(): Boolean {
        return try {
            val requestId = ++stopRequestSerial
            val future = stopExecutor.submit<Boolean> {
                LlamaNativeBridge.nativeStopGeneration()
                true
            }
            future.get(READY_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) { false }
    }

    fun safeGenerate(prompt: String?, maxTokens: Int): String {
        LlamaJniStreaming.resetStreamingFilters()
        return try {
            LlamaJniStreaming.sanitizeGeneratedText(LlamaNativeBridge.generate(prompt, maxTokens) ?: "")
        } catch (t: Throwable) { "" }
    }

    fun safeGenerateAsync(prompt: String?, maxTokens: Int): Boolean {
        LlamaMetricsManager.reset()
        LlamaMetricsManager.generationStartMs = System.currentTimeMillis()
        LlamaMetricsManager.updateMetrics { it.copy(isGenerating = true) }
        return try { LlamaNativeBridge.generateAsync(prompt, maxTokens) } catch (t: Throwable) { false }
    }

    fun safeSetSystemPrompt(prompt: String): Boolean {
        if (prompt.isBlank() || lastAppliedSystemPrompt == prompt) return prompt.isNotBlank()
        val res = LlamaNativeBridge.setOption("system_prompt", prompt)
        if (res) lastAppliedSystemPrompt = prompt
        return res
    }

    fun safeSetOption(key: String?, value: String?): Boolean {
        if (key.isNullOrBlank()) return false
        return try {
            LlamaNativeBridge.setOption(key, value)
        } catch (t: Throwable) {
            Log.w(TAG, "safeSetOption failed for key=$key", t)
            false
        }
    }

    fun safeInitModel(path: String?, useGpu: Boolean): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val provider = resourceProvider
            val ok = if (provider != null) {
                LlamaEngineController.loadModel(path, provider, appContext)
            } else {
                LlamaNativeBridge.loadModel(path)
            }
            modelLoaded = ok
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "safeInitModel failed", t)
            false
        }
    }

    fun safeIsReady(): Boolean {
        return try {
            modelLoaded
        } catch (_: Throwable) {
            false
        }
    }

    fun safeNativeGenerateStream(prompt: String?, maxTokens: Int): Boolean {
        return try {
            if (maxTokens > 0) {
                generateAsync(prompt, maxTokens)
            } else {
                LlamaNativeBridge.nativeGenerateStream(prompt)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "safeNativeGenerateStream failed", t)
            false
        }
    }

    fun getNativeRuntimeDiagnostics(): NativeRuntimeDiagnostics {
        return LlamaJniDiagnostics.getNativeRuntimeDiagnostics(
            configuredThreadCount = configuredThreadCount,
            configuredContextLength = configuredContextLength,
            modelInfo = null
        )
    }

    fun safeNativeGetStateData(): ByteArray? = null

    fun safeNativeLoadStateData(stateData: ByteArray?): Boolean = false

    val chunkFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    @JvmStatic
    fun onNativeChunk(chunk: String?) {
        try {
            if (chunk != null) {
                val normalizedChunk = LlamaJniStreaming.sanitizeChunkForStreaming(chunk)
                val now = System.currentTimeMillis()
                LlamaMetricsManager.updateLastChunkTime(now)

                if (!LlamaMetricsManager.firstTokenLatencyLogged && LlamaMetricsManager.generationStartMs > 0L) {
                    val latency = now - LlamaMetricsManager.generationStartMs
                    LlamaMetricsManager.firstTokenLatencyLogged = true
                    LlamaMetricsManager.updateMetrics { it.copy(firstTokenLatencyMs = latency) }
                }

                LlamaMetricsManager.streamedTokenCount += 1
                val elapsedMs = (now - LlamaMetricsManager.generationStartMs).coerceAtLeast(1L)
                val tokensPerSecond = LlamaMetricsManager.streamedTokenCount * 1000.0 / elapsedMs.toDouble()

                LlamaMetricsManager.updateMetrics {
                    it.copy(
                        isGenerating = true,
                        streamedTokenCount = LlamaMetricsManager.streamedTokenCount,
                        tokensPerSecond = tokensPerSecond
                    )
                }

                if (normalizedChunk.isNotBlank()) {
                    chunkFlow.tryEmit(normalizedChunk)
                }
            }
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun onNativeFinish() {
        try {
            LlamaJniStreaming.resetStreamingFilters()
            LlamaMetricsManager.updateMetrics { it.copy(isGenerating = false) }
            LlamaMetricsManager.incrementFinishSequence()
        } catch (_: Throwable) {}
    }
}
