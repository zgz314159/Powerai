package com.example.powerai.feature.searchchat

import android.os.SystemClock
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.domain.repository.WebSearchRepository
import com.example.powerai.domain.repository.WebSearchResult
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedGroundedPrompt(
    val question: String,
    val prompt: String,
    val promptPrefixKey: String,
    val references: List<KnowledgeItem>,
    val webSources: List<String> = emptyList(),
    val retrievalMs: Long,
    val promptBuildMs: Long,
    val promptChars: Int,
    val prefixReuseHit: Boolean,
    val answerMode: SmartAnswerMode,
    val backendLabel: String,
) {
    fun stageMetrics(
        threadLabel: String,
        batchLabel: String,
        batchThreadLabel: String,
        prefixReuseEnabled: Boolean,
    ): SmartStageMetrics {
        return SmartStageMetrics(
            retrievalMs = retrievalMs,
            promptBuildMs = promptBuildMs,
            evidenceCount = references.size + webSources.size,
            promptChars = promptChars,
            modeLabel = answerMode.label,
            backendLabel = backendLabel,
            threadLabel = threadLabel,
            batchLabel = batchLabel,
            batchThreadLabel = batchThreadLabel,
            prefixReuseEnabled = prefixReuseEnabled,
        )
    }
}

@Singleton
class DeepSeekPromptManager
    @Inject
    constructor(
        private val retrievalFusionUseCase: RetrievalFusionUseCase,
        private val webSearchRepository: WebSearchRepository,
    ) {
        data class CachedPromptPrefix(
            val key: String,
            val prefix: String,
        )

        private var cachedPromptPrefix: CachedPromptPrefix? = null

        suspend fun prepareGroundedPrompt(
            normalizedQuestion: String,
            answerMode: SmartAnswerMode,
            prefixReuseEnabled: Boolean,
            backendLabel: String,
            provider: NativeResourceProvider,
            onProgress: (SmartProgressPhase, String, String) -> Unit,
            webSearchEnabled: Boolean = false,
        ): PreparedGroundedPrompt {
            onProgress(
                SmartProgressPhase.RETRIEVING,
                "正在检索本地资料",
                "优先查找与问题最相关的本地知识片段。",
            )

            val retrievalStartedAt = SystemClock.elapsedRealtime()
            val references =
                withContext(Dispatchers.IO) {
                    retrievalFusionUseCase.invoke(normalizedQuestion, limit = answerMode.evidenceLimit, forceAnn = true)
                }

            val webResults = fetchWebResultsIfEnabled(normalizedQuestion, webSearchEnabled, onProgress)
            val webSources = webResults.map { it.url }.filter { it.isNotBlank() }.distinct()
            val retrievalMs = SystemClock.elapsedRealtime() - retrievalStartedAt

            SmartDeepSeekDebugLogger.logEvent(
                provider = provider,
                tag = "RETRIEVAL",
                message =
                    "durationMs=$retrievalMs evidenceCount=${references.size} webCount=${webResults.size} " +
                        "titles=${references.joinToString(" | ") { it.title.take(24) }}",
            )

            onProgress(
                SmartProgressPhase.BUILDING_PROMPT,
                "正在组织问题与证据",
                buildPromptProgressDetail(references.size, webResults.size, webSearchEnabled),
            )

            val promptBuildStartedAt = SystemClock.elapsedRealtime()
            val promptPrefixKey = buildPromptPrefixCacheKey(references, answerMode, webSources, webSearchEnabled)
            val cachedPrefix = cachedPromptPrefix
            val prefixReuseHit = prefixReuseEnabled && cachedPrefix?.key == promptPrefixKey

            val promptPrefix =
                if (prefixReuseHit && cachedPrefix != null) {
                    cachedPrefix.prefix
                } else {
                    val evidenceText = buildEvidenceText(references, answerMode, webResults)
                    buildPromptPrefix(evidenceText, hasWeb = webResults.isNotEmpty()).also { prefix ->
                        if (prefixReuseEnabled) {
                            cachedPromptPrefix =
                                CachedPromptPrefix(
                                    key = promptPrefixKey,
                                    prefix = prefix,
                                )
                        }
                    }
                }

            val prompt = promptPrefix + normalizedQuestion
            val promptBuildMs = SystemClock.elapsedRealtime() - promptBuildStartedAt

            SmartDeepSeekDebugLogger.logEvent(
                provider = provider,
                tag = "PROMPT",
                message =
                    "durationMs=$promptBuildMs chars=${prompt.length} prefixReuseHit=$prefixReuseHit " +
                        "content=${prompt.replace("\n", " ").take(240)}",
            )

            return PreparedGroundedPrompt(
                question = normalizedQuestion,
                prompt = prompt,
                promptPrefixKey = promptPrefixKey,
                references = references,
                webSources = webSources,
                retrievalMs = retrievalMs,
                promptBuildMs = promptBuildMs,
                promptChars = prompt.length,
                prefixReuseHit = prefixReuseHit,
                answerMode = answerMode,
                backendLabel = backendLabel,
            )
        }

        private suspend fun fetchWebResultsIfEnabled(
            query: String,
            webSearchEnabled: Boolean,
            onProgress: (SmartProgressPhase, String, String) -> Unit,
        ): List<WebSearchResult> {
            if (!webSearchEnabled) return emptyList()

            val configured =
                try {
                    webSearchRepository.isConfigured()
                } catch (_: Throwable) {
                    false
                }
            if (!configured) {
                onProgress(
                    SmartProgressPhase.RETRIEVING,
                    "联网未配置",
                    "未配置 SERPER_API_KEY，本次仅使用本地资料。",
                )
                return emptyList()
            }

            onProgress(
                SmartProgressPhase.RETRIEVING,
                "正在检索网页资料",
                "联网已开启，正在补充公开网页摘要。",
            )
            return withContext(Dispatchers.IO) {
                try {
                    webSearchRepository.search(query, count = 5)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        }

        private fun buildPromptProgressDetail(
            localCount: Int,
            webCount: Int,
            webSearchEnabled: Boolean,
        ): String {
            return when {
                localCount == 0 && webCount == 0 && webSearchEnabled ->
                    "本地与网页均未命中，改为按常识兜底生成。"
                localCount == 0 && webCount == 0 ->
                    "未命中本地资料，改为按常识兜底生成。"
                localCount > 0 && webCount > 0 ->
                    "已命中 $localCount 条本地 + $webCount 条网页资料，正在拼接提示词。"
                webCount > 0 ->
                    "已命中 $webCount 条网页资料，正在拼接提示词。"
                else ->
                    "已命中 $localCount 条本地资料，正在拼接提示词。"
            }
        }

        fun buildPromptPrefixCacheKey(
            references: List<KnowledgeItem>,
            mode: SmartAnswerMode,
            webSources: List<String> = emptyList(),
            webSearchEnabled: Boolean = false,
        ): String {
            val localPart =
                if (references.isEmpty()) {
                    "base"
                } else {
                    "ids:" + references.map { it.id }.sorted().joinToString(",")
                }
            val webPart =
                if (!webSearchEnabled || webSources.isEmpty()) {
                    "web:off"
                } else {
                    "web:" + webSources.sorted().joinToString(",")
                }
            return "$localPart::mode:${mode.name}::$webPart"
        }

        private fun buildEvidenceText(
            references: List<KnowledgeItem>,
            mode: SmartAnswerMode,
            webResults: List<WebSearchResult>,
        ): String {
            if (references.isEmpty() && webResults.isEmpty()) return ""
            return buildString {
                if (references.isNotEmpty()) {
                    append("相关本地资料片段：\n")
                    references.take(mode.evidenceLimit).forEachIndexed { index, item ->
                        append("[${index + 1}] ${item.title}\n")
                        append("内容：${item.content.take(1200)}\n\n")
                    }
                }
                if (webResults.isNotEmpty()) {
                    append("相关网页资料：\n")
                    webResults.forEachIndexed { index, item ->
                        append("[网${index + 1}] ${item.title}\n")
                        if (item.snippet.isNotBlank()) {
                            append("摘要：${item.snippet.take(800)}\n")
                        }
                        if (item.url.isNotBlank()) {
                            append("链接：${item.url}\n")
                        }
                        append('\n')
                    }
                }
            }
        }

        private fun buildPromptPrefix(
            evidenceText: String,
            hasWeb: Boolean,
        ): String {
            return buildString {
                append("你是一个专业的电力知识助手。请基于以下参考资料回答用户的问题。\n\n")
                if (evidenceText.isNotBlank()) {
                    append(evidenceText)
                    append("--- 以上为参考资料 ---\n\n")
                }
                append("要求：\n")
                append("1. 如果资料中没有相关信息，请明确告知，不要胡乱编造。\n")
                append("2. 请尽量引用资料中的原文，并在回答中标注引用编号，如 [1]")
                if (hasWeb) {
                    append(" 或 [网1]")
                }
                append("。\n")
                append("3. 回答应专业、准确、简洁。\n\n")
                append("用户问题：")
            }
        }

        fun clearCache() {
            cachedPromptPrefix = null
        }
    }
