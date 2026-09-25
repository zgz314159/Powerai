package com.example.powerai.domain.query

import com.example.powerai.domain.query.QueryIntent

data class DefinitionAlias(
    val pattern: String,
    val cue: String
)

data class QueryAlias(
    val pattern: String,
    val canonical: String,
    val intents: Set<QueryIntent> = emptySet(),
    val applyWhenIntentUnknown: Boolean = true,
    val trigger: QueryAliasTrigger = QueryAliasTrigger()
)

data class QueryAliasTrigger(
    val subjectTokens: Set<String> = emptySet(),
    val actionTokens: Set<String> = emptySet(),
    val forbiddenTokens: Set<String> = emptySet(),
    val requiredDomains: Set<QueryAliasSemanticDomain> = emptySet(),
    val forbiddenDomains: Set<QueryAliasSemanticDomain> = emptySet()
) {
    fun matches(query: String): Boolean {
        val domains = QueryAliasSemanticDomain.detect(query)
        val subjectMatched = subjectTokens.isEmpty() || subjectTokens.any(query::contains)
        val actionMatched = actionTokens.isEmpty() || actionTokens.any(query::contains)
        val forbiddenMatched = forbiddenTokens.any(query::contains)
        val requiredDomainMatched = requiredDomains.isEmpty() || requiredDomains.any(domains::contains)
        val forbiddenDomainMatched = forbiddenDomains.any(domains::contains)
        return subjectMatched && actionMatched && !forbiddenMatched && requiredDomainMatched && !forbiddenDomainMatched
    }
}

enum class QueryAliasSemanticDomain {
    DEVICE_OPERATION,
    PROCEDURE_ANALYSIS,
    COMPARISON,
    SAFETY_CONCEPT;

    companion object {
        fun detect(query: String): Set<QueryAliasSemanticDomain> = buildSet {
            if (listOf("变压", "断路", "隔离开", "熔丝", "倒闸", "停运", "送电").any(query::contains)) {
                add(DEVICE_OPERATION)
            }
            if (listOf("分析", "怎么", "咋", "步骤", "流程", "处理", "处置").any(query::contains)) {
                add(PROCEDURE_ANALYSIS)
            }
            if (listOf("区别", "差别", "不同", "不一", "比较", "对比").any(query::contains)) {
                add(COMPARISON)
            }
            if (listOf("跨步电压", "跨步压", "接地", "电压", "电流").any(query::contains)) {
                add(SAFETY_CONCEPT)
            }
        }
    }
}

enum class DefinitionAliasGroup {
    PURPOSE,
    DEFINITION
}

enum class QueryAliasGroup {
    DEVICES,
    ACTION_RULES,
    QUESTION_STYLE,
    SAFETY_TERMS
}

object QueryAliasCatalog {
    val definitionAliasGroups: Map<DefinitionAliasGroup, List<DefinitionAlias>> = linkedMapOf(
        DefinitionAliasGroup.PURPOSE to listOf(
            DefinitionAlias(pattern = "有什么用", cue = "用途"),
            DefinitionAlias(pattern = "有啥用", cue = "用途"),
            DefinitionAlias(pattern = "有何用", cue = "用途"),
            DefinitionAlias(pattern = "有什么作用", cue = "作用"),
            DefinitionAlias(pattern = "有啥作用", cue = "作用"),
            DefinitionAlias(pattern = "是干什么的", cue = "用途"),
            DefinitionAlias(pattern = "干什么用", cue = "用途"),
            DefinitionAlias(pattern = "干啥用", cue = "用途")
        ),
        DefinitionAliasGroup.DEFINITION to listOf(
            DefinitionAlias(pattern = "是什么意思", cue = "定义"),
            DefinitionAlias(pattern = "是啥意思", cue = "定义")
        )
    )

    val queryAliasGroups: Map<QueryAliasGroup, List<QueryAlias>> = linkedMapOf(
        QueryAliasGroup.DEVICES to listOf(
            QueryAlias(pattern = "隔开", canonical = "隔离开关"),
            QueryAlias(pattern = "断电器", canonical = "断路器"),
            QueryAlias(
                pattern = "电容",
                canonical = "电容器",
                trigger = QueryAliasTrigger(
                    actionTokens = setOf("用途", "作用", "功能", "定义", "含义", "概念", "原理", "目的", "特点")
                )
            )
        ),
        QueryAliasGroup.ACTION_RULES to listOf(
            QueryAlias(
                pattern = "换熔丝",
                canonical = "更换熔丝",
                trigger = QueryAliasTrigger(
                    subjectTokens = setOf("变压器"),
                    actionTokens = setOf("熔丝"),
                    requiredDomains = setOf(QueryAliasSemanticDomain.DEVICE_OPERATION),
                    forbiddenDomains = setOf(QueryAliasSemanticDomain.PROCEDURE_ANALYSIS)
                )
            )
        ),
        QueryAliasGroup.QUESTION_STYLE to listOf(
            QueryAlias(
                pattern = "有啥差别",
                canonical = "有什么区别",
                intents = setOf(QueryIntent.COMPARISON),
                applyWhenIntentUnknown = false,
                trigger = QueryAliasTrigger(
                    requiredDomains = setOf(QueryAliasSemanticDomain.COMPARISON),
                    forbiddenDomains = setOf(QueryAliasSemanticDomain.PROCEDURE_ANALYSIS)
                )
            ),
            QueryAlias(
                pattern = "有啥不一样",
                canonical = "有什么不同",
                intents = setOf(QueryIntent.COMPARISON),
                applyWhenIntentUnknown = false,
                trigger = QueryAliasTrigger(
                    requiredDomains = setOf(QueryAliasSemanticDomain.COMPARISON),
                    forbiddenDomains = setOf(QueryAliasSemanticDomain.PROCEDURE_ANALYSIS)
                )
            ),
            QueryAlias(
                pattern = "咋分析",
                canonical = "怎么分析",
                intents = setOf(QueryIntent.PROCEDURE),
                applyWhenIntentUnknown = false,
                trigger = QueryAliasTrigger(
                    requiredDomains = setOf(QueryAliasSemanticDomain.PROCEDURE_ANALYSIS),
                    forbiddenDomains = setOf(QueryAliasSemanticDomain.COMPARISON)
                )
            )
        ),
        QueryAliasGroup.SAFETY_TERMS to listOf(
            QueryAlias(pattern = "跨步压", canonical = "跨步电压")
        )
    )

    val definitionAliases: List<DefinitionAlias>
        get() = definitionAliasGroups.values.flatten()

    val queryAliases: List<QueryAlias>
        get() = queryAliasGroups.values.flatten()

    fun definitionAliases(group: DefinitionAliasGroup): List<DefinitionAlias> {
        return definitionAliasGroups[group].orEmpty()
    }

    fun queryAliases(group: QueryAliasGroup): List<QueryAlias> {
        return queryAliasGroups[group].orEmpty()
    }
}
