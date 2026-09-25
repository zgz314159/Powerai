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
import com.example.powerai.core.model.KnowledgeItem

import android.os.SystemClock
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.feature.searchchat.RetrievalFusionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedGroundedPrompt(
    val question: String,
    val prompt: String,
    val promptPrefixKey: String,
    val references: List<KnowledgeItem>,
    val retrievalMs: Long,
    val promptBuildMs: Long,
    val promptChars: Int,
    val prefixReuseHit: Boolean,
    val answerMode: SmartAnswerMode,
    val backendLabel: String
) {
    fun stageMetrics(
        threadLabel: String,
        batchLabel: String,
        batchThreadLabel: String,
        prefixReuseEnabled: Boolean
    ): SmartStageMetrics {
        return SmartStageMetrics(
            retrievalMs = retrievalMs,
            promptBuildMs = promptBuildMs,
            evidenceCount = references.size,
            promptChars = promptChars,
            modeLabel = answerMode.label,
            backendLabel = backendLabel,
            threadLabel = threadLabel,
            batchLabel = batchLabel,
            batchThreadLabel = batchThreadLabel,
            prefixReuseEnabled = prefixReuseEnabled
        )
    }
}

@Singleton
class DeepSeekPromptManager @Inject constructor(
    private val retrievalFusionUseCase: RetrievalFusionUseCase
) {
    data class CachedPromptPrefix(
        val key: String,
        val prefix: String
    )

    private var cachedPromptPrefix: CachedPromptPrefix? = null

    suspend fun prepareGroundedPrompt(
        normalizedQuestion: String,
        answerMode: SmartAnswerMode,
        prefixReuseEnabled: Boolean,
        backendLabel: String,
        provider: NativeResourceProvider,
        onProgress: (SmartProgressPhase, String, String) -> Unit
    ): PreparedGroundedPrompt {
        onProgress(
            SmartProgressPhase.RETRIEVING,
            "正在检索本地资料",
            "优先查找与问题最相关的本地知识片段。"
        )

        val retrievalStartedAt = SystemClock.elapsedRealtime()
        val references = withContext(Dispatchers.IO) {
            retrievalFusionUseCase.invoke(normalizedQuestion, limit = answerMode.evidenceLimit, forceAnn = true)
        }
        val retrievalMs = SystemClock.elapsedRealtime() - retrievalStartedAt

        SmartDeepSeekDebugLogger.logEvent(
            provider = provider,
            tag = "RETRIEVAL",
            message = "durationMs=$retrievalMs evidenceCount=${references.size} titles=${references.joinToString(" | ") { it.title.take(24) }}"
        )

        onProgress(
            SmartProgressPhase.BUILDING_PROMPT,
            "正在组织问题与证据",
            if (references.isEmpty()) {
                "未命中本地资料，改为按常识兜底生成。"
            } else {
                "已命中 ${references.size} 条本地资料，正在拼接提示词。"
            }
        )

        val promptBuildStartedAt = SystemClock.elapsedRealtime()
        val promptPrefixKey = buildPromptPrefixCacheKey(references, answerMode)
        val cachedPrefix = cachedPromptPrefix
        val prefixReuseHit = prefixReuseEnabled && cachedPrefix?.key == promptPrefixKey

        val promptPrefix = if (prefixReuseHit && cachedPrefix != null) {
            cachedPrefix.prefix
        } else {
            val evidenceText = buildEvidenceText(references, answerMode)
            buildPromptPrefix(evidenceText, answerMode).also { prefix ->
                if (prefixReuseEnabled) {
                    cachedPromptPrefix = CachedPromptPrefix(
                        key = promptPrefixKey,
                        prefix = prefix
                    )
                }
            }
        }

        val prompt = promptPrefix + normalizedQuestion
        val promptBuildMs = SystemClock.elapsedRealtime() - promptBuildStartedAt

        SmartDeepSeekDebugLogger.logEvent(
            provider = provider,
            tag = "PROMPT",
            message = "durationMs=$promptBuildMs chars=${prompt.length} prefixReuseHit=$prefixReuseHit content=${prompt.replace("\n", " ").take(240)}"
        )

        return PreparedGroundedPrompt(
            question = normalizedQuestion,
            prompt = prompt,
            promptPrefixKey = promptPrefixKey,
            references = references,
            retrievalMs = retrievalMs,
            promptBuildMs = promptBuildMs,
            promptChars = prompt.length,
            prefixReuseHit = prefixReuseHit,
            answerMode = answerMode,
            backendLabel = backendLabel
        )
    }

    fun buildPromptPrefixCacheKey(references: List<KnowledgeItem>, mode: SmartAnswerMode): String {
        if (references.isEmpty()) return "base::${mode.name}"
        val ids = references.map { it.id }.sorted().joinToString(",")
        return "ids:$ids::mode:${mode.name}"
    }

    private fun buildEvidenceText(references: List<KnowledgeItem>, mode: SmartAnswerMode): String {
        if (references.isEmpty()) return ""
        return buildString {
            append("相关资料片段：\n")
            references.take(mode.evidenceLimit).forEachIndexed { index, item ->
                append("[${index + 1}] ${item.title}\n")
                append("内容：${item.content.take(1200)}\n\n")
            }
        }
    }

    private fun buildPromptPrefix(evidenceText: String, mode: SmartAnswerMode): String {
        return buildString {
            append("你是一个专业的电力知识助手。请基于以下参考资料回答用户的问题。\n\n")
            if (evidenceText.isNotBlank()) {
                append(evidenceText)
                append("--- 以上为参考资料 ---\n\n")
            }
            append("要求：\n")
            append("1. 如果资料中没有相关信息，请明确告知，不要胡乱编造。\n")
            append("2. 请尽量引用资料中的原文，并在回答中标注引用编号，如 [1]。\n")
            append("3. 回答应专业、准确、简洁。\n\n")
            append("用户问题：")
        }
    }

    fun clearCache() {
        cachedPromptPrefix = null
    }
}
