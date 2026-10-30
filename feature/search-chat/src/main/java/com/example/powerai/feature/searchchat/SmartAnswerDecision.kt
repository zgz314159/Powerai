package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.query.QueryIntent
import com.example.powerai.domain.query.QueryUnderstandingPipeline

/**
 * Smart 页证据决策（概念 FULL/PARTIAL/CLARIFY/NONE）。
 * 相关度分数只辅助；意图覆盖与来源资格在上层已过滤。
 */
enum class SmartAnswerDecisionKind {
    FULL,
    PARTIAL,
    CLARIFY,
    NONE,
}

data class SmartAnswerDecision(
    val kind: SmartAnswerDecisionKind,
    val reasonCode: String,
    val missingHints: List<String> = emptyList(),
    val directAnswer: String? = null,
)

object SmartAnswerDecisionEngine {
    private val procedureCue = Regex("怎么|如何|怎样|步骤|流程|操作|要领")

    fun decide(
        question: String,
        qualifiedEvidence: List<KnowledgeItem>,
        confidence: SmartEvidenceConfidence,
        unverifiedOnlyNotice: String? = null,
    ): SmartAnswerDecision {
        if (SmartQueryIntent.isAmbiguousOrTooShort(question)) {
            return SmartAnswerDecision(
                kind = SmartAnswerDecisionKind.CLARIFY,
                reasonCode = "NEEDS_CLARIFICATION",
                directAnswer = SmartQueryIntent.clarificationMessage(question),
            )
        }
        if (qualifiedEvidence.isEmpty()) {
            return SmartAnswerDecision(
                kind = SmartAnswerDecisionKind.NONE,
                reasonCode =
                    if (!unverifiedOnlyNotice.isNullOrBlank()) {
                        "NO_QUALIFIED_EVIDENCE"
                    } else {
                        "NO_QUALIFIED_EVIDENCE"
                    },
                directAnswer = unverifiedOnlyNotice ?: SmartAnswerGuard.INSUFFICIENT,
            )
        }

        val understanding = QueryUnderstandingPipeline.understand(question)
        val procedureAsk =
            understanding.intent == QueryIntent.PROCEDURE || procedureCue.containsMatchIn(question)
        val best = qualifiedEvidence.maxOf { SmartEvidenceGrader.answerabilityScore(question, it) }
        val gaps = missingCoverageHints(question, qualifiedEvidence)
        val usefulPartial = hasPartialUsefulCoverage(question, qualifiedEvidence)

        return when {
            // 流程问：证据只覆盖职责/片段 → PARTIAL 抽原文，禁止交给模型自由编步骤
            procedureAsk && usefulPartial &&
                (confidence != SmartEvidenceConfidence.CORRECT || gaps.isNotEmpty()) ->
                SmartAnswerDecision(
                    kind = SmartAnswerDecisionKind.PARTIAL,
                    reasonCode = "PARTIAL_COVERAGE",
                    missingHints = gaps,
                    directAnswer = buildPartialAnswer(qualifiedEvidence, gaps),
                )

            confidence == SmartEvidenceConfidence.CORRECT && best >= 0.55f && gaps.isEmpty() ->
                SmartAnswerDecision(
                    kind = SmartAnswerDecisionKind.FULL,
                    reasonCode = "FULL_COVERAGE",
                )

            // 有方面/实体真实重合的局部证据 → PARTIAL；近邻噪声（手套≠绝缘电阻）→ NONE
            usefulPartial &&
                (
                    confidence == SmartEvidenceConfidence.AMBIGUOUS ||
                        gaps.isNotEmpty()
                ) ->
                SmartAnswerDecision(
                    kind = SmartAnswerDecisionKind.PARTIAL,
                    reasonCode = "PARTIAL_COVERAGE",
                    missingHints = gaps,
                    directAnswer = buildPartialAnswer(qualifiedEvidence, gaps),
                )

            else ->
                SmartAnswerDecision(
                    kind = SmartAnswerDecisionKind.NONE,
                    reasonCode = "NO_QUALIFIED_EVIDENCE",
                    directAnswer = SmartAnswerGuard.INSUFFICIENT,
                )
        }
    }

    /** 是否具备可展示的局部事实（方面或实体在证据中真实命中），避免近邻噪声当 PARTIAL。 */
    fun hasPartialUsefulCoverage(
        question: String,
        items: List<KnowledgeItem>,
    ): Boolean {
        if (items.isEmpty()) return false
        val hay = items.joinToString("\n") { "${it.title}\n${it.content}" }
        val aspects = SmartEvidenceGrader.extractAspects(question)
        if (aspects.isNotEmpty()) {
            val ordered = aspects.sortedByDescending { it.length }
            return ordered.any { hay.contains(it) }
        }
        val entities = SmartEvidenceGrader.extractEntities(question)
        if (entities.isNotEmpty()) {
            return entities.any { hay.contains(it) }
        }
        return items.maxOf { SmartEvidenceGrader.answerabilityScore(question, it) } >= 0.40f
    }

    fun missingCoverageHints(
        question: String,
        items: List<KnowledgeItem>,
    ): List<String> {
        val hay = items.joinToString("\n") { "${it.title}\n${it.content}" }
        val hints = mutableListOf<String>()
        if (procedureCue.containsMatchIn(question)) {
            val hasOrder = Regex("先|后|然后|再|步骤|依次").containsMatchIn(hay)
            val hasDuty = Regex("监护|操作人|二人|两人").containsMatchIn(hay)
            if (!hasOrder) hints += "完整操作先后顺序"
            if (hasDuty && !hasOrder) hints += "除人员分工外的其余步骤"
            if (!Regex("验电|检电").containsMatchIn(hay) && question.contains("停电")) {
                hints += "检电/验电要求"
            }
            if (!Regex("接地").containsMatchIn(hay) && question.contains("停电")) {
                hints += "接地封线要求"
            }
        }
        return hints.distinct()
    }

    fun buildPartialAnswer(
        items: List<KnowledgeItem>,
        gaps: List<String>,
    ): String {
        val top = items.first()
        val excerpt = top.content.trim()
        return buildString {
            append("资料仅覆盖部分内容，不能当作完整操作流程。\n")
            append("摘录（").append(top.title.take(40)).append("）：\n")
            append(excerpt)
            if (gaps.isNotEmpty()) {
                append("\n\n尚未覆盖：")
                append(gaps.joinToString("、"))
                append("。请补充更具体的问题后再问。")
            } else {
                append("\n\n如需完整流程，请补充对象、电压等级与具体步骤点。")
            }
        }
    }
}
