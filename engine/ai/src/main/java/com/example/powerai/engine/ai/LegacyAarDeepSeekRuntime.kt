package com.example.powerai.engine.ai

import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class LegacyAarDeepSeekRuntime : DeepSeekNativeRuntime {
    override val generationMetrics: StateFlow<GenerationMetrics> = LlamaJni.generationMetrics
    override val finishSequence: StateFlow<Long> = LlamaJni.finishSequence
    override val lastChunkAtMs: StateFlow<Long> = LlamaJni.lastChunkAtMs
    override val chunkFlow: SharedFlow<String> = LlamaJni.chunkFlow

    override fun provideResources(provider: NativeResourceProvider, context: android.content.Context?) = LlamaJni.provideResources(provider, context)

    override fun configureRuntime(threadCount: Int, contextLength: Int) {
        LlamaJni.configureRuntime(threadCount, contextLength)
    }

    override fun configureStreamingThinking(enabled: Boolean) {
        LlamaJni.configureStreamingThinking(enabled)
    }

    override fun safeSetOption(key: String?, value: String?): Boolean = LlamaJni.safeSetOption(key, value)

    override fun safeInitBackend(backend: String?): Boolean = LlamaJni.safeInitBackend(backend)

    override fun safeInitModel(path: String?, useGpu: Boolean): Boolean = LlamaJni.safeInitModel(path, useGpu)

    override fun safeUnload(): Boolean = LlamaJni.safeUnload()

    override fun safeIsReady(): Boolean = LlamaJni.safeIsReady()

    override fun safeStopGeneration(): Boolean = LlamaJni.safeStopGeneration()

    override fun safeGenerate(prompt: String?, maxTokens: Int): String = LlamaJni.safeGenerate(prompt, maxTokens)

    override fun safeNativeGenerateStream(prompt: String?, maxTokens: Int): Boolean {
        return LlamaJni.safeNativeGenerateStream(prompt, maxTokens)
    }

    override fun safeSetSystemPrompt(prompt: String): Boolean = LlamaJni.safeSetSystemPrompt(prompt)

    override fun getNativeRuntimeDiagnostics(): NativeRuntimeDiagnostics {
        return LlamaJni.getNativeRuntimeDiagnostics()
    }

    override fun safeNativeGetStateData(): ByteArray? = LlamaJni.safeNativeGetStateData()

    override fun safeNativeLoadStateData(stateData: ByteArray?): Boolean {
        return LlamaJni.safeNativeLoadStateData(stateData)
    }
}
