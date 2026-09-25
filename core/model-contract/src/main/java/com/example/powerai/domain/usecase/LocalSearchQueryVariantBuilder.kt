package com.example.powerai.domain.usecase

import com.example.powerai.domain.query.QueryIntent
import com.example.powerai.domain.query.QueryRewritePlanner
import com.example.powerai.domain.query.QueryUnderstandingPipeline

data class LocalSearchQueryVariant(
    val text: String,
    val strategy: String,
    val priority: Int
)

object LocalSearchQueryVariantBuilder {
    fun build(query: String): List<String> {
        return buildPlan(query).map { it.text }
    }

    fun buildPlan(query: String): List<LocalSearchQueryVariant> {
        val understanding = QueryUnderstandingPipeline.understand(query)
        val normalized = understanding.normalizedQuery
        if (normalized.isBlank()) return emptyList()

        val variants = linkedMapOf<String, LocalSearchQueryVariant>()
        fun addVariant(text: String, strategy: String, priority: Int) {
            val normalizedText = QueryRewritePlanner.normalizeQuery(text)
            if (normalizedText.length < 2) return
            val existing = variants[normalizedText]
            if (existing == null || priority < existing.priority) {
                variants[normalizedText] = LocalSearchQueryVariant(
                    text = normalizedText,
                    strategy = strategy,
                    priority = priority
                )
            } else if (shouldPromoteStrategy(existing.strategy, strategy)) {
                variants[normalizedText] = existing.copy(strategy = strategy)
            }
        }

        addVariant(normalized, strategy = "original", priority = 0)
        understanding.retrievalQueries.forEachIndexed { index, variant ->
            addVariant(
                text = variant,
                strategy = if (index == 0) "normalized" else "rewrite_${index}",
                priority = index + 1
            )
        }

        if (QueryRewritePlanner.isDefinitionStyleQuery(normalized)) {
            val subject = QueryRewritePlanner.extractDefinitionSubject(normalized).orEmpty()
            val cue = QueryRewritePlanner.extractDefinitionCue(normalized).orEmpty()
            if (subject.isNotBlank()) {
                addVariant(subject, strategy = "definition_subject", priority = 20)
                addVariant("$subject ${cue.ifBlank { "用途" }}", strategy = "definition_subject_cue", priority = 21)
                addVariant("$subject 的${cue.ifBlank { "主要用途" }}是什么", strategy = "definition_question", priority = 22)
                addVariant("$subject 答", strategy = "qa_answer_hint", priority = 23)
                addAnswerOrientedDefinitionVariants(addVariant = ::addVariant, subject = subject, cue = cue)
            }
        }

        if (QueryRewritePlanner.isConditionStyleQuery(normalized)) {
            addAnswerOrientedConditionVariants(normalized, addVariant = ::addVariant)
        }

        when (understanding.intent) {
            QueryIntent.PROCEDURE -> addProcedureVariants(normalized, addVariant = ::addVariant)
            QueryIntent.COMPARISON -> addComparisonVariants(normalized, addVariant = ::addVariant)
            else -> Unit
        }

        return variants.values.sortedBy { it.priority }
    }

    fun isConditionStyleQuery(query: String): Boolean {
        return QueryRewritePlanner.isConditionStyleQuery(query)
    }

    private fun shouldPromoteStrategy(existing: String, incoming: String): Boolean {
        if (existing == incoming) return false
        return isGenericStrategy(existing) && !isGenericStrategy(incoming)
    }

    private fun isGenericStrategy(strategy: String): Boolean {
        return strategy == "original" || strategy == "normalized" || strategy.startsWith("rewrite_")
    }

    private fun addAnswerOrientedDefinitionVariants(
        addVariant: (String, String, Int) -> Unit,
        subject: String,
        cue: String
    ) {
        when (cue.ifBlank { "用途" }) {
            "主要用途", "用途", "作用", "功能", "目的", "特点" -> {
                addVariant("$subject 的${cue.ifBlank { "主要用途" }}是", "answer_oriented", 24)
                addVariant("答 $subject 的${cue.ifBlank { "主要用途" }}是", "answer_oriented", 25)
                addVariant("$subject 主要用于", "answer_oriented", 26)
                addVariant("$subject 用于", "answer_oriented", 27)
            }

            "定义", "含义", "概念" -> {
                addVariant("$subject 是指", "answer_oriented", 24)
                addVariant("答 $subject 是指", "answer_oriented", 25)
            }

            "原理" -> {
                addVariant("$subject 的工作原理是", "answer_oriented", 24)
                addVariant("答 $subject 的工作原理是", "answer_oriented", 25)
            }
        }
    }

    private fun addAnswerOrientedConditionVariants(
        normalized: String,
        addVariant: (String, String, Int) -> Unit
    ) {
        val subject = extractConditionSubject(normalized)
        if (subject.isBlank()) return
        addVariant("$subject 应立即停止运行", "answer_oriented", 30)
        addVariant("$subject 有下列情况之一时 应立即停止运行", "answer_oriented", 31)
        addVariant("$subject 停运条件", "answer_oriented", 32)
    }

    private fun extractConditionSubject(normalized: String): String {
        val compact = normalized.replace(" ", "")
        val phrase = listOf("停止运行", "停运").firstOrNull(compact::contains) ?: return ""
        return compact.substringBefore(phrase)
            .replace("在什么情况下", "")
            .replace("什么情况下", "")
            .replace("啥情况下", "")
            .replace("啥情况", "")
            .replace("哪些情况下", "")
            .replace("何种情况下", "")
            .replace("在何种情况下", "")
            .replace("什么情形下", "")
            .replace("在什么情形下", "")
            .removeSuffix("的")
            .trim()
    }

    private fun addProcedureVariants(
        normalized: String,
        addVariant: (String, String, Int) -> Unit
    ) {
        val subject = extractProcedureSubject(normalized)
        if (subject.isBlank()) return
        addVariant("$subject 操作步骤", "procedure_outline", 40)
        addVariant("$subject 操作流程", "procedure_outline", 41)
        addVariant("$subject 处理流程", "procedure_outline", 42)
        addVariant("$subject 先 后", "answer_oriented", 43)
    }

    private fun addComparisonVariants(
        normalized: String,
        addVariant: (String, String, Int) -> Unit
    ) {
        val pair = extractComparisonPair(normalized)
        if (pair == null) return
        val (left, right) = pair
        addVariant("$left $right 区别", "comparison_outline", 50)
        addVariant("$left 与 $right 的区别", "comparison_outline", 51)
        addVariant("$left 和 $right 的不同点", "comparison_outline", 52)
        addVariant("$left 与 $right 相比", "answer_oriented", 53)
    }

    private fun extractProcedureSubject(normalized: String): String {
        val cleaned = normalized
            .replace("如何", "")
            .replace("怎么", "")
            .replace("怎样", "")
            .replace("咋", "")
            .replace("怎么办", "")
            .replace("步骤", "")
            .replace("流程", "")
            .replace("操作", "")
            .replace("处置", "")
            .replace("处理", "")
            .trim()
            .removeSuffix("的")
            .trim()

        return reorderProcedureSubject(cleaned)
    }

    private fun reorderProcedureSubject(subject: String): String {
        val compact = subject.replace(" ", "")
        val reorderedVerb = listOf("分析", "检查", "测量", "试验")
            .firstOrNull { verb -> compact.endsWith(verb) && compact.length > verb.length }
            ?.let { verb -> "$verb${compact.removeSuffix(verb)}" }

        return reorderedVerb ?: compact
    }

    private fun extractComparisonPair(normalized: String): Pair<String, String>? {
        val compact = normalized.replace(" ", "")
        val separators = listOf("与", "和", "跟")
        val suffixes = listOf("区别", "不同", "差异", "对比", "比较", "不一样")
        val separator = separators.firstOrNull(compact::contains) ?: return null
        val suffix = suffixes.firstOrNull(compact::contains) ?: return null
        val left = compact.substringBefore(separator).trim().removeSuffix("的")
        val rightRaw = compact.substringAfter(separator)
        val right = rightRaw.substringBefore(suffix)
            .replace("有啥", "")
            .replace("有什么", "")
            .replace("有何", "")
            .replace("哪里", "")
            .trim()
            .removeSuffix("的")
        return if (left.length >= 1 && right.length >= 1) left to right else null
    }
}