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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val queryKind: SmartQueryKind = SmartQueryKind.DOMAIN,
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

        companion object {
            /** 本地证据截断：短而准，控制手机 prefill。 */
            const val LOCAL_SNIPPET_CHARS = 480

            /** 网页摘要截断：只用 Serper snippet，不拉整页。 */
            const val WEB_SNIPPET_CHARS = 280

            const val WEB_RESULT_LIMIT = 3
        }

        suspend fun prepareGroundedPrompt(
            normalizedQuestion: String,
            answerMode: SmartAnswerMode,
            prefixReuseEnabled: Boolean,
            backendLabel: String,
            provider: NativeResourceProvider,
            onProgress: (SmartProgressPhase, String, String) -> Unit,
            webSearchEnabled: Boolean = false,
            /** 测试可注入；生产传 null 则用设备当天日期。避免默认参数调用实例方法导致 mock `$default` 异常。 */
            deviceToday: String? = null,
        ): PreparedGroundedPrompt {
            val today = deviceToday?.takeIf { it.isNotBlank() } ?: deviceToday()
            val queryKind = SmartQueryRouter.classify(normalizedQuestion)

            onProgress(
                SmartProgressPhase.RETRIEVING,
                "正在检索本地资料",
                "优先查找与问题最相关的本地知识片段。",
            )

            val retrievalStartedAt = SystemClock.elapsedRealtime()
            val references =
                withContext(Dispatchers.IO) {
                    retrievalFusionUseCase.invoke(
                        normalizedQuestion,
                        limit = answerMode.evidenceLimit,
                        forceAnn = true,
                    )
                }

            val wantWeb = SmartQueryRouter.shouldFetchWeb(queryKind, webSearchEnabled, references.size)
            val webResults = fetchWebResultsIfNeeded(normalizedQuestion, wantWeb, webSearchEnabled, onProgress)
            val webSources = webResults.map { it.url }.filter { it.isNotBlank() }.distinct()
            val retrievalMs = SystemClock.elapsedRealtime() - retrievalStartedAt

            SmartDeepSeekDebugLogger.logEvent(
                provider = provider,
                tag = "RETRIEVAL",
                message =
                    "durationMs=$retrievalMs kind=$queryKind evidenceCount=${references.size} " +
                        "webCount=${webResults.size} wantWeb=$wantWeb " +
                        "titles=${references.joinToString(" | ") { it.title.take(24) }}",
            )

            onProgress(
                SmartProgressPhase.BUILDING_PROMPT,
                "正在组织问题与证据",
                buildPromptProgressDetail(references.size, webResults.size, queryKind, webSearchEnabled),
            )

            val promptBuildStartedAt = SystemClock.elapsedRealtime()
            val promptPrefixKey =
                buildPromptPrefixCacheKey(references, answerMode, webSources, wantWeb, today, queryKind)
            val cachedPrefix = cachedPromptPrefix
            val prefixReuseHit = prefixReuseEnabled && cachedPrefix?.key == promptPrefixKey

            val promptPrefix =
                if (prefixReuseHit) {
                    cachedPrefix!!.prefix
                } else {
                    val evidenceText = buildEvidenceText(references, answerMode, webResults)
                    buildPromptPrefix(
                        evidenceText = evidenceText,
                        hasWeb = webResults.isNotEmpty(),
                        hasLocal = references.isNotEmpty(),
                        deviceToday = today,
                        queryKind = queryKind,
                    ).also { prefix ->
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
                queryKind = queryKind,
            )
        }

        private suspend fun fetchWebResultsIfNeeded(
            query: String,
            wantWeb: Boolean,
            webSearchEnabled: Boolean,
            onProgress: (SmartProgressPhase, String, String) -> Unit,
        ): List<WebSearchResult> {
            if (!wantWeb) return emptyList()

            val configured =
                try {
                    webSearchRepository.isConfigured()
                } catch (_: Throwable) {
                    false
                }
            if (!configured) {
                if (webSearchEnabled) {
                    onProgress(
                        SmartProgressPhase.RETRIEVING,
                        "联网未配置",
                        "未配置 SERPER_API_KEY，本次仅使用本地资料与设备日期。",
                    )
                }
                return emptyList()
            }

            onProgress(
                SmartProgressPhase.RETRIEVING,
                "正在检索网页资料",
                "本地不足或时效问题，正在补充公开网页摘要。",
            )
            return withContext(Dispatchers.IO) {
                try {
                    webSearchRepository.search(query, count = WEB_RESULT_LIMIT)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        }

        private fun buildPromptProgressDetail(
            localCount: Int,
            webCount: Int,
            kind: SmartQueryKind,
            webSearchEnabled: Boolean,
        ): String {
            return when {
                localCount == 0 && webCount == 0 ->
                    "未命中可用资料，将要求模型明确说明资料不足（不常识兜底）。"
                localCount > 0 && webCount > 0 ->
                    "已命中 $localCount 条本地 + $webCount 条网页，正在拼接短证据。"
                webCount > 0 ->
                    "已命中 $webCount 条网页摘要，正在拼接短证据。"
                kind == SmartQueryKind.TEMPORAL && webSearchEnabled ->
                    "已命中 $localCount 条本地；时效问题可叠加网页，本次未取到网页。"
                else ->
                    "已命中 $localCount 条本地资料，正在拼接短证据。"
            }
        }

        fun buildPromptPrefixCacheKey(
            references: List<KnowledgeItem>,
            mode: SmartAnswerMode,
            webSources: List<String> = emptyList(),
            webSearchEnabled: Boolean = false,
            deviceToday: String = "",
            queryKind: SmartQueryKind = SmartQueryKind.DOMAIN,
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
            return "$localPart::mode:${mode.name}::$webPart::day:$deviceToday::kind:${queryKind.name}"
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
                        append("内容：${trimSnippet(item.content, LOCAL_SNIPPET_CHARS)}\n\n")
                    }
                }
                if (webResults.isNotEmpty()) {
                    append("相关网页资料：\n")
                    webResults.take(WEB_RESULT_LIMIT).forEachIndexed { index, item ->
                        append("[网${index + 1}] ${item.title}\n")
                        if (item.snippet.isNotBlank()) {
                            append("摘要：${trimSnippet(item.snippet, WEB_SNIPPET_CHARS)}\n")
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
            hasLocal: Boolean,
            deviceToday: String,
            queryKind: SmartQueryKind,
        ): String {
            return buildString {
                append("你是一个专业的电力知识助手。\n")
                append("今天日期：$deviceToday\n\n")
                if (evidenceText.isNotBlank()) {
                    append(evidenceText)
                    append("--- 以上为参考资料 ---\n\n")
                } else {
                    append("（本次未检索到可用参考资料）\n\n")
                }
                append("硬性要求：\n")
                append("1. 只能根据上述参考资料与「今天日期」回答；禁止使用训练记忆或常识编造。\n")
                append("2. 资料不足以回答时，必须原样回复：资料不足，无法回答。\n")
                if (hasLocal || hasWeb) {
                    append("3. 引用资料时标注编号，如 [1]")
                    if (hasWeb) append(" 或 [网1]")
                    append("；数字与条文尽量照抄原文。\n")
                } else {
                    append("3. 没有资料时不要猜测，不要改写用户问题，不要输出分析过程。\n")
                }
                when (queryKind) {
                    SmartQueryKind.TEMPORAL ->
                        append("4. 若问题询问今天/当前日期，直接依据「今天日期」作答。\n")
                    SmartQueryKind.DOMAIN ->
                        append("4. 规程/安规问题以本地资料为准；资料没有就判定不足。\n")
                    SmartQueryKind.CHITCHAT ->
                        append("4. 寒暄可简短回应；涉及事实仍须有资料依据。\n")
                }
                append("5. 回答应专业、准确、简洁。\n\n")
                append("用户问题：")
            }
        }

        private fun trimSnippet(
            text: String,
            maxChars: Int,
        ): String {
            val normalized = text.replace(Regex("\\s+"), " ").trim()
            if (normalized.length <= maxChars) return normalized
            return normalized.take(maxChars).trimEnd() + "…"
        }

        fun deviceToday(): String {
            return try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            } catch (_: Throwable) {
                ""
            }
        }

        fun clearCache() {
            cachedPromptPrefix = null
        }
    }
