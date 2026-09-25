package com.example.powerai.domain.query

object QueryIntentClassifier {
    private val definitionTokens = listOf(
        "用途", "作用", "功能", "定义", "含义", "概念", "原理", "目的", "特点"
    )

    private val comparisonTokens = listOf(
        "区别", "差异", "差别", "不同", "对比", "比较", "相比", "不一样", "哪个更", "哪种更"
    )

    private val procedureTokens = listOf(
        "步骤", "流程", "如何", "怎么", "怎样", "咋", "怎么办", "处置", "处理", "操作"
    )

    private val factTokens = listOf(
        "什么", "啥", "为何", "为什么", "是否", "能否", "哪些", "哪个", "多少", "多久", "何时", "吗", "?", "？"
    )

    fun classify(query: String): QueryIntent {
        val normalized = QueryRewritePlanner.normalizeQuery(query).lowercase()
        if (normalized.isBlank()) return QueryIntent.FACT_QUESTION

        return when {
            comparisonTokens.any(normalized::contains) -> QueryIntent.COMPARISON
            procedureTokens.any(normalized::contains) && !QueryRewritePlanner.isConditionStyleQuery(normalized) -> QueryIntent.PROCEDURE
            QueryRewritePlanner.isConditionStyleQuery(normalized) -> QueryIntent.FACT_QUESTION
            QueryRewritePlanner.isActionRuleStyleQuery(normalized) -> QueryIntent.FACT_QUESTION
            QueryRewritePlanner.isDefinitionStyleQuery(normalized) -> QueryIntent.FACT_QUESTION
            definitionTokens.any(normalized::contains) -> QueryIntent.FACT_QUESTION
            factTokens.any(normalized::contains) -> QueryIntent.FACT_QUESTION
            else -> QueryIntent.TOPIC_OVERVIEW
        }
    }
}
