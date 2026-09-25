package com.example.powerai.engine.ai

import android.util.Log
import com.example.powerai.engine.ai.BuildConfig
import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

object DeepSeekNativeRuntimeBridge {
    private const val TAG = "DeepSeekRuntimeBridge"
    private val legacyRuntime: DeepSeekNativeRuntime = LegacyAarDeepSeekRuntime()
    private val llamaCppRuntime: DeepSeekNativeRuntime = LlamaCppDeepSeekRuntime()

    @Volatile
    private var activeRuntime: DeepSeekNativeRuntime = selectedRuntime()

    val generationMetrics: StateFlow<GenerationMetrics>
        get() = activeRuntime.generationMetrics

    val finishSequence: StateFlow<Long>
        get() = activeRuntime.finishSequence

    val lastChunkAtMs: StateFlow<Long>
        get() = activeRuntime.lastChunkAtMs

    val chunkFlow: SharedFlow<String>
        get() = activeRuntime.chunkFlow

    fun provideResources(provider: NativeResourceProvider, context: android.content.Context? = null) {
        val effectiveContext = context ?: provider.getContext() as? android.content.Context
        legacyRuntime.provideResources(provider, effectiveContext)
        llamaCppRuntime.provideResources(provider, effectiveContext)
        LlamaJni.provideResources(provider, effectiveContext)
        activeRuntime = selectedRuntime()
    }

    fun configureRuntime(threadCount: Int, contextLength: Int = LlamaJni.DEFAULT_CONTEXT_LENGTH) {
        legacyRuntime.configureRuntime(threadCount, contextLength)
        llamaCppRuntime.configureRuntime(threadCount, contextLength)
        activeRuntime.configureRuntime(threadCount, contextLength)
    }

    fun configureStreamingThinking(enabled: Boolean) {
        activeRuntime.configureStreamingThinking(enabled)
    }

    fun safeSetOption(key: String?, value: String?): Boolean {
        return activeRuntime.safeSetOption(key, value)
    }

    fun safeInitBackend(backend: String?): Boolean {
        activeRuntime = selectedRuntime()
        Log.i(TAG, "safeInitBackend using runtime=${runtimeName(activeRuntime)} buildSelfJni=${BuildConfig.DEEPSEEK_SELF_BUILT_JNI_ENABLED} backend=$backend")
        if (activeRuntime.safeInitBackend(backend)) return true
        Log.w(TAG, "safeInitBackend failed on ${runtimeName(activeRuntime)}, fallback to legacy")
        return fallbackToLegacy { it.safeInitBackend(backend) }
    }

    fun safeInitModel(path: String?, useGpu: Boolean): Boolean {
        Log.i(TAG, "safeInitModel using runtime=${runtimeName(activeRuntime)} buildSelfJni=${BuildConfig.DEEPSEEK_SELF_BUILT_JNI_ENABLED} path=${path ?: ""}")
        if (activeRuntime.safeInitModel(path, useGpu)) return true
        Log.w(TAG, "safeInitModel failed on ${runtimeName(activeRuntime)}, fallback to legacy")
        return fallbackToLegacy { it.safeInitModel(path, useGpu) }
    }

    fun safeUnload(): Boolean {
        val unloadOk = activeRuntime.safeUnload()
        activeRuntime = selectedRuntime()
        return unloadOk
    }

    fun safeIsReady(): Boolean = activeRuntime.safeIsReady()

    fun safeStopGeneration(): Boolean = activeRuntime.safeStopGeneration()

    fun safeGenerate(prompt: String?, maxTokens: Int): String {
        return activeRuntime.safeGenerate(prompt, maxTokens)
    }

    fun safeNativeGenerateStream(prompt: String?, maxTokens: Int = 0): Boolean {
        return activeRuntime.safeNativeGenerateStream(prompt, maxTokens)
    }

    fun safeSetSystemPrompt(prompt: String): Boolean = activeRuntime.safeSetSystemPrompt(prompt)

    fun getNativeRuntimeDiagnostics(): NativeRuntimeDiagnostics {
        return activeRuntime.getNativeRuntimeDiagnostics()
    }

    fun safeNativeGetStateData(): ByteArray? = activeRuntime.safeNativeGetStateData()

    fun safeNativeLoadStateData(stateData: ByteArray?): Boolean {
        return activeRuntime.safeNativeLoadStateData(stateData)
    }

    private fun selectedRuntime(): DeepSeekNativeRuntime {
        return if (BuildConfig.DEEPSEEK_SELF_BUILT_JNI_ENABLED) llamaCppRuntime else legacyRuntime
    }

    private inline fun fallbackToLegacy(block: (DeepSeekNativeRuntime) -> Boolean): Boolean {
        if (activeRuntime === legacyRuntime) return false
        activeRuntime = legacyRuntime
        return block(legacyRuntime)
    }

    private fun runtimeName(runtime: DeepSeekNativeRuntime): String {
        return when (runtime) {
            llamaCppRuntime -> "llamaCpp"
            legacyRuntime -> "legacyAar"
            else -> runtime::class.java.simpleName
        }
    }
}
