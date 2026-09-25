package com.example.powerai.engine.ai

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nehuatl.llamacpp.LlamaContext

class LlamaCppDeepSeekRuntime : DeepSeekNativeRuntime {
    private companion object {
        const val TAG = "LlamaCppRuntime"
    }

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextContextId = AtomicInteger(1)
    private val _generationMetrics = MutableStateFlow(GenerationMetrics())
    private val _finishSequence = MutableStateFlow(0L)
    private val _lastChunkAtMs = MutableStateFlow(0L)
    private val _chunkFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override val generationMetrics: StateFlow<GenerationMetrics> = _generationMetrics.asStateFlow()
    override val finishSequence: StateFlow<Long> = _finishSequence.asStateFlow()
    override val lastChunkAtMs: StateFlow<Long> = _lastChunkAtMs.asStateFlow()
    override val chunkFlow: SharedFlow<String> = _chunkFlow.asSharedFlow()

    private var resourceProvider: NativeResourceProvider? = null
    @Volatile
    private var configuredThreadCount: Int = 2
    @Volatile
    private var configuredContextLength: Int = LlamaJni.DEFAULT_CONTEXT_LENGTH
    @Volatile
    private var configuredBatchSize: Int = 512
    @Volatile
    private var requestedBackend: String? = null
    @Volatile
    private var systemPrompt: String? = null
    @Volatile
    private var activeContext: LlamaContext? = null
    @Volatile
    private var activeModelPath: String? = null
    @Volatile
    private var generationStartMs: Long = 0L
    @Volatile
    private var activeCompletionJob: Job? = null

    override fun provideResources(provider: NativeResourceProvider, context: android.content.Context?) {
        resourceProvider = provider
    }

    override fun configureRuntime(threadCount: Int, contextLength: Int) {
        configuredThreadCount = threadCount.coerceAtLeast(1)
        configuredContextLength = contextLength.coerceAtLeast(256)
    }

    override fun configureStreamingThinking(enabled: Boolean) {
    }

    override fun safeSetOption(key: String?, value: String?): Boolean {
        val normalizedKey = key?.trim()?.lowercase() ?: return false
        val normalizedValue = value?.trim() ?: return false
        return when (normalizedKey) {
            "n_threads" -> {
                configuredThreadCount = normalizedValue.toIntOrNull()?.coerceAtLeast(1) ?: return false
                true
            }
            "n_ctx" -> {
                configuredContextLength = normalizedValue.toIntOrNull()?.coerceAtLeast(256) ?: return false
                true
            }
            "n_batch" -> {
                configuredBatchSize = normalizedValue.toIntOrNull()?.coerceAtLeast(1) ?: return false
                true
            }
            "n_threads_batch", "cache_prompt" -> true
            else -> false
        }
    }

    override fun safeInitBackend(backend: String?): Boolean {
        requestedBackend = backend
        return !backend.isNullOrBlank()
    }

    override fun safeInitModel(path: String?, useGpu: Boolean): Boolean {
        val modelPath = path?.trim().orEmpty()
        if (modelPath.isEmpty()) return false

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            Log.e(TAG, "Model file missing: $modelPath")
            return false
        }

        return try {
            val pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val detachedFd = pfd.detachFd()

            val runtime = LlamaContext(
                id = nextContextId.getAndIncrement(),
                params = mapOf(
                    "model" to modelFile.absolutePath,
                    "model_fd" to detachedFd,
                    "embedding" to false,
                    "n_ctx" to configuredContextLength,
                    "n_batch" to configuredBatchSize,
                    "n_threads" to configuredThreadCount,
                    "n_gpu_layers" to 0,
                    "use_mlock" to false,
                    "use_mmap" to true,
                    "vocab_only" to false
                )
            )
            runtime.setTokenCallback { token -> onToken(token) }
            releaseActiveContext()
            activeContext = runtime
            activeModelPath = modelPath
            resetMetrics()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "safeInitModel failed", t)
            false
        }
    }

    override fun safeUnload(): Boolean {
        return try {
            safeStopGeneration()
            releaseActiveContext()
            activeModelPath = null
            resetMetrics()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "safeUnload failed", t)
            false
        }
    }

    override fun safeIsReady(): Boolean = activeContext != null

    override fun safeStopGeneration(): Boolean {
        val runtime = activeContext ?: return false
        return try {
            runtime.stopCompletion()
            activeCompletionJob?.cancel()
            _generationMetrics.value = _generationMetrics.value.copy(isGenerating = false)
            _finishSequence.value = _finishSequence.value + 1L
            true
        } catch (t: Throwable) {
            Log.e(TAG, "safeStopGeneration failed", t)
            false
        }
    }

    override fun safeGenerate(prompt: String?, maxTokens: Int): String {
        val runtime = activeContext ?: return ""
        return try {
            markGenerationStart()
            val result = runtime.completion(buildCompletionParams(prompt, maxTokens, emitPartial = false))
            result["text"]?.toString().orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "safeGenerate failed", t)
            ""
        } finally {
            markGenerationFinished()
        }
    }

    override fun safeNativeGenerateStream(prompt: String?, maxTokens: Int): Boolean {
        val runtime = activeContext ?: return false
        if (activeCompletionJob?.isActive == true) return false

        markGenerationStart()
        activeCompletionJob = runtimeScope.launch {
            try {
                runtime.completion(buildCompletionParams(prompt, maxTokens, emitPartial = true))
            } catch (t: Throwable) {
                Log.e(TAG, "streaming completion failed", t)
            } finally {
                markGenerationFinished()
            }
        }
        return true
    }

    override fun safeSetSystemPrompt(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        systemPrompt = prompt
        return true
    }

    override fun getNativeRuntimeDiagnostics(): NativeRuntimeDiagnostics {
        return NativeRuntimeDiagnostics(
            requestedBackend = requestedBackend,
            effectiveBackend = "cpu",
            gpuOffloadActive = false,
            backendInitOpencl = false,
            backendInitVulkan = false,
            configuredThreadCount = configuredThreadCount,
            configuredContextLength = configuredContextLength,
            lastInitParamsJson = "{\"runtime\":\"llamaCpp\",\"n_threads\":$configuredThreadCount,\"n_ctx\":$configuredContextLength,\"n_batch\":$configuredBatchSize}",
            lastRequestedGpuLayers = 0,
            modelInfo = activeModelPath
        )
    }

    override fun safeNativeGetStateData(): ByteArray? = null

    override fun safeNativeLoadStateData(stateData: ByteArray?): Boolean = false

    private fun buildCompletionParams(
        prompt: String?,
        maxTokens: Int,
        emitPartial: Boolean
    ): Map<String, Any> {
        val mergedPrompt = buildPrompt(prompt.orEmpty())
        return mapOf(
            "prompt" to mergedPrompt,
            "n_threads" to configuredThreadCount,
            "n_predict" to maxTokens,
            "emit_partial_completion" to emitPartial,
            "stop" to emptyList<String>()
        )
    }

    private fun buildPrompt(prompt: String): String {
        val appliedSystemPrompt = systemPrompt?.trim().orEmpty()
        return if (appliedSystemPrompt.isBlank()) {
            prompt
        } else {
            appliedSystemPrompt + "\n\n" + prompt
        }
    }

    private fun onToken(token: String) {
        if (token.isEmpty()) return
        val now = System.currentTimeMillis()
        _lastChunkAtMs.value = now
        val current = _generationMetrics.value
        val tokenCount = current.streamedTokenCount + 1
        val firstTokenLatency = current.firstTokenLatencyMs ?: (now - generationStartMs).coerceAtLeast(0L)
        val elapsedMs = (now - generationStartMs).coerceAtLeast(1L)
        _generationMetrics.value = current.copy(
            isGenerating = true,
            firstTokenLatencyMs = firstTokenLatency,
            streamedTokenCount = tokenCount,
            tokensPerSecond = tokenCount * 1000.0 / elapsedMs
        )
        _chunkFlow.tryEmit(token)
    }

    private fun markGenerationStart() {
        generationStartMs = System.currentTimeMillis()
        _lastChunkAtMs.value = generationStartMs
        _generationMetrics.value = GenerationMetrics(isGenerating = true)
    }

    private fun markGenerationFinished() {
        _generationMetrics.value = _generationMetrics.value.copy(isGenerating = false)
        _finishSequence.value = _finishSequence.value + 1L
        activeCompletionJob = null
    }

    private fun releaseActiveContext() {
        try {
            activeContext?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "releaseActiveContext failed", t)
        } finally {
            activeContext = null
        }
    }

    private fun resetMetrics() {
        generationStartMs = 0L
        _generationMetrics.value = GenerationMetrics()
        _lastChunkAtMs.value = 0L
    }
}
