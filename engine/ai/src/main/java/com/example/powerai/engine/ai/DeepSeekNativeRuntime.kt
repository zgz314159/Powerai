package com.example.powerai.engine.ai

import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface DeepSeekNativeRuntime {
    val generationMetrics: StateFlow<GenerationMetrics>
    val finishSequence: StateFlow<Long>
    val lastChunkAtMs: StateFlow<Long>
    val chunkFlow: SharedFlow<String>

    fun provideResources(provider: NativeResourceProvider, context: android.content.Context? = null)

    fun configureRuntime(
        threadCount: Int,
        contextLength: Int = LlamaJni.DEFAULT_CONTEXT_LENGTH
    )

    fun configureStreamingThinking(enabled: Boolean)

    fun safeSetOption(key: String?, value: String?): Boolean

    fun safeInitBackend(backend: String?): Boolean

    fun safeInitModel(path: String?, useGpu: Boolean): Boolean

    fun safeUnload(): Boolean

    fun safeIsReady(): Boolean

    fun safeStopGeneration(): Boolean

    fun safeGenerate(prompt: String?, maxTokens: Int): String

    fun safeNativeGenerateStream(prompt: String?, maxTokens: Int = 0): Boolean

    fun safeSetSystemPrompt(prompt: String): Boolean

    fun getNativeRuntimeDiagnostics(): NativeRuntimeDiagnostics

    fun safeNativeGetStateData(): ByteArray?

    fun safeNativeLoadStateData(stateData: ByteArray?): Boolean
}
