package com.example.powerai.engine.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.core.model.NativeResourceProvider
import java.io.File

/**
 * DeepSeekEngine - 用于集成 deepseek-r1-7b (gguf) 的引擎实现骨架。 */
class DeepSeekEngine(private val ragCoordinator: RAGDataCoordinator) : PowerAIEngine {
    private companion object {
        const val TAG = "DeepSeekEngine"
        const val STREAM_COMPLETION_IDLE_MS = 1_500L
        const val STREAM_COMPLETION_GRACE_MS = 5_000L
    }

    private val nativeRuntime = DeepSeekNativeRuntimeBridge

    private data class InferenceBudget(
        val prompt: String,
        val maxTokens: Int
    )

    private val _thinking = MutableSharedFlow<String>(replay = 0)
    override val thinkingFlow: SharedFlow<String>? = _thinking

    override val chunkFlow: SharedFlow<String> = nativeRuntime.chunkFlow
    override val metrics: StateFlow<GenerationMetrics> = nativeRuntime.generationMetrics

    @Volatile
    private var loaded = false
    private var resourceProvider: NativeResourceProvider? = null
    private var lastError: String? = null

    override suspend fun init(provider: NativeResourceProvider) {
        this.resourceProvider = provider
        nativeRuntime.provideResources(provider)
    }

    override suspend fun setSystemPrompt(prompt: String): Boolean {
        return nativeRuntime.safeSetSystemPrompt(prompt)
    }

    override suspend fun configureStreamingThinking(enabled: Boolean) {
        nativeRuntime.configureStreamingThinking(enabled)
    }

    override suspend fun stopGeneration(): Boolean {
        return nativeRuntime.safeStopGeneration()
    }

    override suspend fun configureRuntime(threadCount: Int, contextLength: Int) {
        nativeRuntime.configureRuntime(threadCount, contextLength)
    }

    override suspend fun setOption(key: String, value: String): Boolean {
        return nativeRuntime.safeSetOption(key, value)
    }

    override suspend fun getStateData(): ByteArray? {
        return nativeRuntime.safeNativeGetStateData()
    }

    override suspend fun loadStateData(data: ByteArray?): Boolean {
        return nativeRuntime.safeNativeLoadStateData(data)
    }

    override suspend fun loadModel(modelPath: String, useGpu: Boolean): Boolean {
        val provider = resourceProvider ?: return false

        var resolved = resolveModelPath(provider, modelPath)
        if (resolved == null) {
            lastError = "model path not found: $modelPath"
            loaded = false
            return false
        }

        var loadPath = resolved
        val resFile = File(loadPath)
        if (resFile.isDirectory) {
            val ggufs = resFile.listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }
            if (!ggufs.isNullOrEmpty()) {
                loadPath = ggufs[0].absolutePath
            } else {
                lastError = "model path resolves to directory without gguf file: $loadPath"
                loaded = false
                return false
            }
        }

        val wantGpu = useGpu && provider.isGpuSupported()

        try {
            val backend = if (wantGpu) "vulkan" else "cpu"
            val initOk = nativeRuntime.safeInitBackend(backend)
            if (!initOk) {
                lastError = "failed to init backend: $backend"
                loaded = false
                return false
            }

            val f = File(loadPath)
            if (!f.exists() || f.length() == 0L) {
                lastError = "model file missing or empty: ${f.absolutePath}"
                loaded = false
                return false
            }

            val loadOk = withTimeoutOrNull(25_000) {
                try {
                    val aarOk = nativeRuntime.safeInitModel(loadPath, wantGpu)
                    val ready = nativeRuntime.safeIsReady()
                    aarOk && ready
                } catch (t: Throwable) {
                    false
                }
            } ?: false

            if (!loadOk) {
                lastError = "model init timed out or returned false: $loadPath"
                loaded = false
                return false
            }

            try {
                nativeRuntime.configureStreamingThinking(false)
                nativeRuntime.safeSetSystemPrompt(DeepSeekPromptProfiles.QUICK_SYSTEM_PROMPT)
            } catch (_: Throwable) {}

            loaded = true
            return true
        } catch (t: Throwable) {
            lastError = "exception while loading model: ${t.message}"
            loaded = false
            return false
        }
    }

    // Expose diagnostic message for manager to include in thrown error
    fun getLastError(): String? = lastError

    private fun resolveModelPath(provider: NativeResourceProvider, requestedPath: String): String? {
        try {
            val req = requestedPath.trim()
            if (req.isEmpty()) return null

            // 1) Absolute path
            if (req.startsWith("/")) {
                val f = File(req)
                if (f.exists()) return f.absolutePath
            }

            // 2) Internal files dir
            val filesDir = provider.getFilesDir()
            if (filesDir != null) {
                val candidate = File(filesDir, "models/$req")
                if (candidate.exists() && candidate.isFile) return candidate.absolutePath

                val nameOnly = File(req).name
                val candidate2 = File(filesDir, "models/$nameOnly")
                if (candidate2.exists() && candidate2.isFile) return candidate2.absolutePath
            }

            // 3) Try assets
            val assetName = File(req).name
            val prepared = provider.prepareModelFromAssets(assetName)
            if (prepared != null) return prepared

        } catch (_: Throwable) {}
        return null
    }

    override suspend fun unload() {
        try {
            nativeRuntime.safeUnload()
        } catch (_: Throwable) {}
        loaded = false
    }

    override suspend fun isLoaded(): Boolean = loaded

    private fun buildInferenceBudget(prompt: String, requestedMaxTokens: Int): InferenceBudget {
        val normalized = DeepSeekTextCodec.normalizePrompt(prompt)
            .replace(Regex("\\s+"), " ")
            .trim()
        val safeMaxTokens = requestedMaxTokens.coerceIn(24, LlamaJni.DEFAULT_RECOMMENDED_MAX_TOKENS)
        val promptCharBudget = when {
            safeMaxTokens >= 512 -> 2400
            safeMaxTokens >= 256 -> 1800
            safeMaxTokens >= 128 -> 1200
            else -> 800
        }
        return InferenceBudget(
            prompt = normalized.take(promptCharBudget),
            maxTokens = safeMaxTokens
        )
    }

    private fun isContextOverflowMessage(text: String): Boolean {
        return text.contains("context overflow", ignoreCase = true) ||
            text.contains("shorten your prompt", ignoreCase = true)
    }

    override suspend fun infer(prompt: String, maxTokens: Int, stream: Boolean): String {
        if (!loaded) throw IllegalStateException("DeepSeek model not loaded")
        return withContext(Dispatchers.IO) {
            val resultBuilder = StringBuilder()
            val budget = buildInferenceBudget(prompt, maxTokens)
            var inThinkBlock = false
            var pendingThinkingText = ""
            var pendingTagFragment = ""
            var lastChunkAtMs = System.currentTimeMillis()
            val finishSequenceBeforeStart = nativeRuntime.finishSequence.value

            fun onChunk(chunk: String) {
                lastChunkAtMs = System.currentTimeMillis()
                val parsed = ThinkingInterceptor.parseStreamingChunk(
                    text = chunk,
                    inThinkBlock = inThinkBlock,
                    pendingThinkingText = pendingThinkingText,
                    pendingTagFragment = pendingTagFragment
                )
                inThinkBlock = parsed.inThinkBlock
                pendingThinkingText = parsed.pendingThinkingText
                pendingTagFragment = parsed.pendingTagFragment
                for (t in parsed.completedThinkingSegments) {
                    try { _thinking.tryEmit(DeepSeekTextCodec.normalizeFinalAnswer(t)) } catch (_: Throwable) {}
                }
                val visibleChunk = DeepSeekTextCodec.normalizeChunk(parsed.visibleText)
                if (visibleChunk.isNotEmpty()) {
                    resultBuilder.append(visibleChunk)
                }
            }

            try {
                kotlinx.coroutines.coroutineScope {
                    val collectJob = launch(Dispatchers.IO) {
                        nativeRuntime.chunkFlow.collect { ch -> onChunk(ch) }
                    }

                    val started = nativeRuntime.safeNativeGenerateStream(budget.prompt, budget.maxTokens)
                    if (!started) {
                        collectJob.cancel()
                        val fallback = DeepSeekTextCodec.normalizeChunk(nativeRuntime.safeGenerate(budget.prompt, budget.maxTokens))
                        return@coroutineScope if (fallback.isNotEmpty()) {
                            if (isContextOverflowMessage(fallback)) {
                                "问题太长，已超过当前本地模型上下文上限。请把问题缩短到一句话，或把最大输出 tokens 调低到 64 左右再试。"
                            } else {
                                fallback
                            }
                        } else {
                            "[DeepSeek start stream failed]"
                        }
                    }

                    val completionObserved = withTimeoutOrNull(STREAM_COMPLETION_GRACE_MS) {
                        while (true) {
                            val finished = nativeRuntime.finishSequence.value > finishSequenceBeforeStart
                            val idleMs = System.currentTimeMillis() - lastChunkAtMs
                            if (finished || idleMs >= STREAM_COMPLETION_IDLE_MS) {
                                return@withTimeoutOrNull true
                            }
                            delay(50)
                        }
                    } ?: false

                    collectJob.cancel()
                    val finalText = resultBuilder.toString()
                    if (isContextOverflowMessage(finalText)) {
                        "问题太长，已超过当前本地模型上下文上限。请把问题缩短到一句话，或把最大输出 tokens 调低到 64 左右再试。"
                    } else {
                        finalText
                    }
                }
            } catch (t: Throwable) {
                if (!stream) {
                    "[DeepSeek inference error: ${t.message}]"
                } else {
                    "[DeepSeek streaming error: ${t.message}]"
                }
            }
        }
    }
}
