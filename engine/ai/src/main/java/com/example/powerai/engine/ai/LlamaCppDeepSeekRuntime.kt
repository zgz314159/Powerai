package com.example.powerai.engine.ai

import android.util.Log
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
import net.ladenthin.llama.LlamaIterable
import net.ladenthin.llama.LlamaModel
import net.ladenthin.llama.callback.CancellationToken
import net.ladenthin.llama.parameters.InferenceParameters
import net.ladenthin.llama.parameters.ModelParameters
import java.io.File

/**
 * DeepSeek native runtime backed by [net.ladenthin:llama-android] (llama.cpp GGUF, CPU).
 */
class LlamaCppDeepSeekRuntime : DeepSeekNativeRuntime {
    private companion object {
        const val TAG = "LlamaCppRuntime"
    }

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    private var activeModel: LlamaModel? = null

    @Volatile
    private var activeModelPath: String? = null

    @Volatile
    private var generationStartMs: Long = 0L

    @Volatile
    private var activeCompletionJob: Job? = null

    @Volatile
    private var activeCancelToken: CancellationToken? = null

    @Volatile
    private var activeIterable: LlamaIterable? = null

    private val modelLock = Any()

    override fun provideResources(
        provider: NativeResourceProvider,
        context: android.content.Context?,
    ) {
        resourceProvider = provider
    }

    override fun configureRuntime(
        threadCount: Int,
        contextLength: Int,
    ) {
        configuredThreadCount = threadCount.coerceAtLeast(1)
        configuredContextLength = contextLength.coerceAtLeast(256)
    }

    override fun configureStreamingThinking(enabled: Boolean) {
    }

    override fun safeSetOption(
        key: String?,
        value: String?,
    ): Boolean {
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

    override fun safeInitModel(
        path: String?,
        useGpu: Boolean,
    ): Boolean {
        val modelPath = path?.trim().orEmpty()
        if (modelPath.isEmpty()) return false

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            Log.e(TAG, "Model file missing: $modelPath")
            return false
        }

        return synchronized(modelLock) {
            try {
                safeStopGeneration()
                releaseActiveModel()

                val params =
                    ModelParameters()
                        .setModel(modelFile.absolutePath)
                        .setOffline(true)
                        .setCtxSize(configuredContextLength)
                        .setThreads(configuredThreadCount)
                        .setBatchSize(configuredBatchSize)
                        // CPU-first (KI-21): ignore GPU until llama-android-opencl is validated.
                        .setGpuLayers(0)

                val model = LlamaModel(params)
                activeModel = model
                activeModelPath = modelPath
                resetMetrics()
                Log.i(TAG, "Loaded GGUF via llama-android: $modelPath")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "safeInitModel failed", t)
                releaseActiveModel()
                false
            }
        }
    }

    override fun safeUnload(): Boolean {
        return try {
            safeStopGeneration()
            synchronized(modelLock) {
                releaseActiveModel()
                activeModelPath = null
            }
            resetMetrics()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "safeUnload failed", t)
            false
        }
    }

    override fun safeIsReady(): Boolean = activeModel != null

    override fun safeStopGeneration(): Boolean {
        return try {
            activeCancelToken?.cancel()
            try {
                activeIterable?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "close iterable failed", t)
            }
            activeCompletionJob?.cancel()
            activeCancelToken = null
            activeIterable = null
            _generationMetrics.value = _generationMetrics.value.copy(isGenerating = false)
            _finishSequence.value = _finishSequence.value + 1L
            true
        } catch (t: Throwable) {
            Log.e(TAG, "safeStopGeneration failed", t)
            false
        }
    }

    override fun safeGenerate(
        prompt: String?,
        maxTokens: Int,
    ): String {
        val model = activeModel ?: return ""
        val token = CancellationToken()
        activeCancelToken = token
        return try {
            markGenerationStart()
            val params = buildInferenceParameters(prompt, maxTokens, stream = false)
            model.complete(params, token)
        } catch (t: Throwable) {
            Log.e(TAG, "safeGenerate failed", t)
            ""
        } finally {
            activeCancelToken = null
            markGenerationFinished()
        }
    }

    override fun safeNativeGenerateStream(
        prompt: String?,
        maxTokens: Int,
    ): Boolean {
        val model = activeModel ?: return false
        if (activeCompletionJob?.isActive == true) return false

        val token = CancellationToken()
        activeCancelToken = token
        markGenerationStart()
        activeCompletionJob =
            runtimeScope.launch {
                try {
                    val params = buildInferenceParameters(prompt, maxTokens, stream = true)
                    val iterable = model.generate(params)
                    activeIterable = iterable
                    iterable.use { it ->
                        for (output in it) {
                            if (token.isCancelled) break
                            val text = output.text
                            if (!text.isNullOrEmpty()) {
                                onToken(text)
                            }
                            if (output.stop) break
                        }
                    }
                } catch (t: Throwable) {
                    if (!token.isCancelled) {
                        Log.e(TAG, "streaming completion failed", t)
                    }
                } finally {
                    activeIterable = null
                    activeCancelToken = null
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
            lastInitParamsJson =
                "{\"runtime\":\"llama-android\",\"n_threads\":$configuredThreadCount," +
                    "\"n_ctx\":$configuredContextLength,\"n_batch\":$configuredBatchSize}",
            lastRequestedGpuLayers = 0,
            modelInfo = activeModelPath,
        )
    }

    override fun safeNativeGetStateData(): ByteArray? = null

    override fun safeNativeLoadStateData(stateData: ByteArray?): Boolean = false

    private fun buildInferenceParameters(
        prompt: String?,
        maxTokens: Int,
        stream: Boolean,
    ): InferenceParameters {
        val merged = buildPrompt(prompt.orEmpty())
        var params =
            InferenceParameters.of(merged)
                .withNPredict(maxTokens.coerceAtLeast(1))
                .withStream(stream)
        // Prefer chat-ish stops for Distill/Qwen templates without forcing a full chat API.
        params = params.withStopStrings("</s>", "<|im_end|>", "<|endoftext|>")
        return params
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
        _generationMetrics.value =
            current.copy(
                isGenerating = true,
                firstTokenLatencyMs = firstTokenLatency,
                streamedTokenCount = tokenCount,
                tokensPerSecond = tokenCount * 1000.0 / elapsedMs,
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

    private fun releaseActiveModel() {
        try {
            activeModel?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "releaseActiveModel failed", t)
        } finally {
            activeModel = null
        }
    }

    private fun resetMetrics() {
        generationStartMs = 0L
        _generationMetrics.value = GenerationMetrics()
        _lastChunkAtMs.value = 0L
    }
}
