package com.example.powerai.core.model

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用模型引擎抽象：封装加载卸载与推理接口，便于在运行时切换不同实现。
 */
interface PowerAIEngine {
    suspend fun init(provider: NativeResourceProvider)
    suspend fun loadModel(modelPath: String, useGpu: Boolean = false): Boolean
    suspend fun unload()
    suspend fun isLoaded(): Boolean

    /**
     * 同步/异步推理接口：返回文本结果；实现可选择通过流回调推送中间输出。
     */
    suspend fun infer(prompt: String, maxTokens: Int = 512, stream: Boolean = false): String

    /**
     * 可选的思维链输出流（如果实现支持流式拆分）。
     */
    val thinkingFlow: SharedFlow<String>?

    /**
     * 流式输出原始片段。
     */
    val chunkFlow: SharedFlow<String>?

    /**
     * 实时生成指标。
     */
    val metrics: StateFlow<GenerationMetrics>

    /**
     * 配置系统提示词。
     */
    suspend fun setSystemPrompt(prompt: String): Boolean

    /**
     * 配置是否开启流式思维输出。
     */
    suspend fun configureStreamingThinking(enabled: Boolean)

    /**
     * 停止当前生成。
     */
    suspend fun stopGeneration(): Boolean

    /**
     * 配置引擎运行参数（线程数、上下文长度等）。
     */
    suspend fun configureRuntime(threadCount: Int, contextLength: Int = 1024)

    /**
     * 设置通用选项。
     */
    suspend fun setOption(key: String, value: String): Boolean

    /**
     * 获取当前引擎状态快照。
     */
    suspend fun getStateData(): ByteArray?

    /**
     * 加载状态快照。
     */
    suspend fun loadStateData(data: ByteArray?): Boolean
}
