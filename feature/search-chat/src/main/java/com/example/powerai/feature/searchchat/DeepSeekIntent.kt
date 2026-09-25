package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartThreadPreset

/**
 * DeepSeek 模块的用户意图。
 */
sealed interface DeepSeekIntent {
    /** 从设备路径加载模型。 */
    data class LoadModel(val devicePath: String, val useGpu: Boolean = false) : DeepSeekIntent

    /** 卸载模型 */
    data object UnloadModel : DeepSeekIntent

    /** 生成文本 */
    data class Generate(val prompt: String, val maxTokens: Int = 64) : DeepSeekIntent

    /** 生成基于本地资料的回答。 */
    data class GenerateGroundedAnswer(val question: String, val maxTokens: Int? = null) : DeepSeekIntent

    /** 设置回答模式 */
    data class SetAnswerMode(val mode: SmartAnswerMode) : DeepSeekIntent

    /** 设置后端模式 */
    data class SetBackendMode(val mode: SmartBackendMode) : DeepSeekIntent

    /** 设置线程预设 */
    data class SetThreadPreset(val preset: SmartThreadPreset) : DeepSeekIntent

    /** 设置批处理大小预设。 */
    data class SetBatchSizePreset(val preset: SmartBatchSizePreset) : DeepSeekIntent

    /** 设置批处理线程预设。 */
    data class SetBatchThreadPreset(val preset: SmartBatchThreadPreset) : DeepSeekIntent

    /** 设置前缀复用启用状态。 */
    data class SetPrefixReuseEnabled(val enabled: Boolean) : DeepSeekIntent

    /** 设置状态快照复用启用状态。 */
    data class SetStateSnapshotReuseEnabled(val enabled: Boolean) : DeepSeekIntent

    /** 运行状态快照探测。 */
    data object RunStateSnapshotProbe : DeepSeekIntent

    /** 运行线程延迟基准测试 */
    data class RunThreadLatencyBenchmark(val question: String) : DeepSeekIntent

    /** 运行批处理大小基准测试。 */
    data class RunBatchSizeLatencyBenchmark(val question: String) : DeepSeekIntent

    /** 运行批处理线程基准测试。 */
    data class RunBatchThreadLatencyBenchmark(val question: String) : DeepSeekIntent
}
