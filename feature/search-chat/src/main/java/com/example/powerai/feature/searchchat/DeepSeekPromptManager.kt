package com.example.powerai.feature.searchchat

import android.os.SystemClock
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.core.model.SourceQualification
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
    /** 非空则跳过模型生成，直接展示（澄清 / 资料不足）。 */
    val directAnswer: String? = null,
    val evidenceConfidence: SmartEvidenceConfidence = SmartEvidenceConfidence.INCORRECT,
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

            // RQ-RAG：歧义/过短问先澄清，不检索硬答。
            if (SmartQueryIntent.isAmbiguousOrTooShort(normalizedQuestion)) {
                val clarify = SmartQueryIntent.clarificationMessage(normalizedQuestion)
                onProgress(
                    SmartProgressPhase.COMPLETED,
                    "请补充问题",
                    "问句过短或意图不清，已返回澄清提示。",
                )
                return PreparedGroundedPrompt(
                    question = normalizedQuestion,
                    prompt = "",
                    promptPrefixKey = "clarify::$normalizedQuestion",
                    references = emptyList(),
                    webSources = emptyList(),
                    retrievalMs = 0L,
                    promptBuildMs = 0L,
                    promptChars = 0,
                    prefixReuseHit = false,
                    answerMode = answerMode,
                    backendLabel = backendLabel,
                    queryKind = queryKind,
                    directAnswer = clarify,
                    evidenceConfidence = SmartEvidenceConfidence.INCORRECT,
                )
            }

            onProgress(
                SmartProgressPhase.RETRIEVING,
                "正在检索本地资料",
                "优先查找与问题最相关的本地知识片段。",
            )

            val retrievalStartedAt = SystemClock.elapsedRealtime()
            val retrievalLimit = (answerMode.evidenceLimit * 3).coerceAtLeast(6)
            val rawReferences =
                withContext(Dispatchers.IO) {
                    retrievalFusionUseCase.invoke(
                        normalizedQuestion,
                        limit = retrievalLimit,
                        forceAnn = true,
                    )
                }
            val ranked =
                SmartEvidenceRanker.refine(
                    question = normalizedQuestion,
                    items = rawReferences,
                    limit = answerMode.evidenceLimit,
                )
            val confidence = SmartEvidenceGrader.grade(normalizedQuestion, ranked)
            var references =
                SmartEvidenceGrader.filterByGrade(normalizedQuestion, ranked, confidence)

            // P2：未核定内置摘要不得作为规范操作权威证据（可浏览，但不进生成 Prompt）。
            val (qualifiedRefs, unverifiedRefs) = SourceQualification.partitionForNormativeAnswer(references)
            references = qualifiedRefs

            val wantWeb =
                SmartQueryRouter.shouldFetchWeb(
                    kind = queryKind,
                    webSearchEnabled = webSearchEnabled,
                    localHitCount = references.size,
                    question = normalizedQuestion,
                    localConfidence = confidence,
                )
            val webResults = fetchWebResultsIfNeeded(normalizedQuestion, wantWeb, webSearchEnabled, onProgress)
            val webSources = webResults.map { it.url }.filter { it.isNotBlank() }.distinct()
            val retrievalMs = SystemClock.elapsedRealtime() - retrievalStartedAt

            SmartDeepSeekDebugLogger.logEvent(
                provider = provider,
                tag = "RETRIEVAL",
                message =
                    "durationMs=$retrievalMs kind=$queryKind confidence=$confidence " +
                        "evidenceCount=${references.size} unverifiedSynopsis=${unverifiedRefs.size} " +
                        "webCount=${webResults.size} wantWeb=$wantWeb " +
                        "titles=${references.joinToString(" | ") { it.title.take(24) }}",
            )

            if (references.isEmpty() && webResults.isEmpty() && queryKind != SmartQueryKind.TEMPORAL) {
                val decision =
                    SmartAnswerDecisionEngine.decide(
                        question = normalizedQuestion,
                        qualifiedEvidence = emptyList(),
                        confidence = confidence,
                        unverifiedOnlyNotice =
                            if (unverifiedRefs.isNotEmpty()) {
                                SourceQualification.unverifiedSynopsisNotice(unverifiedRefs)
                            } else {
                                null
                            },
                    )
                onProgress(
                    SmartProgressPhase.COMPLETED,
                    if (unverifiedRefs.isNotEmpty()) "来源未核定" else "资料不足",
                    "decision=${decision.kind} reason=${decision.reasonCode}",
                )
                return PreparedGroundedPrompt(
                    question = normalizedQuestion,
                    prompt = "",
                    promptPrefixKey = "decision-${decision.kind}::$normalizedQuestion",
                    references = emptyList(),
                    webSources = emptyList(),
                    retrievalMs = retrievalMs,
                    promptBuildMs = 0L,
                    promptChars = 0,
                    prefixReuseHit = false,
                    answerMode = answerMode,
                    backendLabel = backendLabel,
                    queryKind = queryKind,
                    directAnswer = decision.directAnswer ?: SmartAnswerGuard.INSUFFICIENT,
                    evidenceConfidence = confidence,
                )
            }

            // TEMPORAL（日期/时效）走设备日期+可选网页；不套规程证据决策短路。
            if (queryKind != SmartQueryKind.TEMPORAL) {
                val decision =
                    SmartAnswerDecisionEngine.decide(
                        question = normalizedQuestion,
                        qualifiedEvidence = references,
                        confidence = confidence,
                    )
                if (decision.kind != SmartAnswerDecisionKind.FULL && webResults.isEmpty()) {
                    onProgress(
                        SmartProgressPhase.COMPLETED,
                        when (decision.kind) {
                            SmartAnswerDecisionKind.PARTIAL -> "局部资料"
                            SmartAnswerDecisionKind.CLARIFY -> "请补充问题"
                            else -> "资料不足"
                        },
                        "decision=${decision.kind} reason=${decision.reasonCode}",
                    )
                    return PreparedGroundedPrompt(
                        question = normalizedQuestion,
                        prompt = "",
                        promptPrefixKey = "decision-${decision.kind}::$normalizedQuestion",
                        // PARTIAL 保留引用卡片；不把未核验生成权交给模型
                        references =
                            if (decision.kind == SmartAnswerDecisionKind.PARTIAL) {
                                references
                            } else {
                                emptyList()
                            },
                        webSources = emptyList(),
                        retrievalMs = retrievalMs,
                        promptBuildMs = 0L,
                        promptChars = 0,
                        prefixReuseHit = false,
                        answerMode = answerMode,
                        backendLabel = backendLabel,
                        queryKind = queryKind,
                        directAnswer = decision.directAnswer ?: SmartAnswerGuard.INSUFFICIENT,
                        evidenceConfidence = confidence,
                    )
                }
            }

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
                directAnswer = null,
                evidenceConfidence = confidence,
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
            // 绑定证据正文指纹与模板模式：仅按 id 排序会在同 id 正文变化或顺序敏感时错复用。
            val localPart =
                if (references.isEmpty()) {
                    "base"
                } else {
                    val idPart = references.map { it.id }.sorted().joinToString(",")
                    val fp =
                        references
                            .sortedBy { it.id }
                            .joinToString("|") { "${it.id}:${it.title}:${it.content.take(160)}" }
                            .hashCode()
                    "ids:$idPart:fp:$fp"
                }
            val webPart =
                if (!webSearchEnabled || webSources.isEmpty()) {
                    "web:off"
                } else {
                    "web:" + webSources.sorted().joinToString(",")
                }
            return "$localPart::mode:${mode.name}::$webPart::day:$deviceToday::kind:${queryKind.name}::tpl:gguf-messages-v1"
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
            // 规则保持短：过长约束会让 1.5B Distill 复述要求/改写问题，反而答崩。
            return buildString {
                append("你是电力知识助手。今天日期：$deviceToday\n\n")
                if (evidenceText.isNotBlank()) {
                    append(evidenceText)
                    append("--- 参考资料结束 ---\n\n")
                } else {
                    append("（无参考资料）\n\n")
                }
                append("要求：只依据参考资料与今天日期作答；没有就回复「资料不足，无法回答」。")
                append("资料与问题主题不符时同样回复「资料不足，无法回答」，禁止脑补数值或常识。")
                if (hasLocal || hasWeb) {
                    append("有相关资料时直接写答案，句末用 [1]")
                    if (hasWeb) append("/[网1]")
                    append(" 标编号；不要另起一行写资料标题。尽量照抄原文。")
                }
                when (queryKind) {
                    SmartQueryKind.TEMPORAL -> append("问日期时用「今天日期」。")
                    SmartQueryKind.DOMAIN -> append("忽略与问题电压/对象不符的资料。")
                    SmartQueryKind.CHITCHAT -> Unit
                }
                append("不要复述问题，不要输出推理过程或单独问号。\n\n")
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
