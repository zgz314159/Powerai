package com.example.powerai.domain.usecase

import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.query.QueryIntent
import com.example.powerai.domain.query.QueryRewritePlanner
import com.example.powerai.domain.query.QueryUnderstandingResult
import java.util.Locale

internal object LocalEvidenceScorer {

    fun informativeTokens(query: String): List<String> {
        return TextSanitizer.normalizeForSearch(query)
            .split(' ')
            .flatMap(LocalEvidenceHeuristics::expandCompactChineseToken)
            .filter { it.isNotBlank() && it.length >= 2 && it !in LocalEvidenceHeuristics.lowSignalTokens }
            .distinct()
    }

    fun informativeTokenGroups(understanding: QueryUnderstandingResult): List<List<String>> {
        val plannedQueries = LocalSearchQueryVariantBuilder.buildPlan(understanding.originalQuery)
            .map(LocalSearchQueryVariant::text)

        return plannedQueries
            .ifEmpty { understanding.retrievalQueries }
            .ifEmpty { listOf(understanding.normalizedQuery) }
            .map(::informativeTokens)
            .filter { it.isNotEmpty() }
            .ifEmpty {
                listOf(informativeTokens(understanding.normalizedQuery)).filter { it.isNotEmpty() }
            }
    }

    fun anchorsFor(intent: QueryIntent, query: String): List<String> = when (intent) {
        QueryIntent.PROCEDURE -> LocalEvidenceHeuristics.procedureAnchors
        QueryIntent.COMPARISON -> LocalEvidenceHeuristics.comparisonAnchors
        QueryIntent.TOPIC_OVERVIEW -> emptyList()
        QueryIntent.FACT_QUESTION -> when {
            QueryRewritePlanner.isDefinitionStyleQuery(query) -> informativeTokens(query).filter(LocalEvidenceHeuristics::isDefinitionSuffixToken)
            query.contains("停止运行") || query.contains("停运") -> LocalEvidenceHeuristics.stopRunAnchors
            QueryRewritePlanner.isActionRuleStyleQuery(query) -> QueryRewritePlanner.extractActionAnchors(query)
            else -> informativeTokens(query).filterNot(LocalEvidenceHeuristics::isDefinitionSuffixToken).take(2)
        }
    }

    fun contextualSignal(
        title: String,
        content: String,
        result: RetrievalResult,
        understanding: QueryUnderstandingResult
    ): Float {
        val source = result.item?.source ?: result.metadata["source"].orEmpty()
        var signal = 0f
        if (LocalEvidenceHeuristics.extractArticleSignature(title, content).isNotBlank()) signal += 0.18f
        if (source.contains("规程") || source.contains("规则") || source.contains("规范")) signal += 0.12f
        val titleNormalized = TextSanitizer.normalizeForSearch(title)
        val queryTokens = informativeTokenGroups(understanding).flatten().distinct().take(3)
        if (queryTokens.count(titleNormalized::contains) >= 2) signal += 0.10f
        if (understanding.signals.contains("action_rule_style")) {
            val anchors = QueryRewritePlanner.extractActionAnchors(understanding.normalizedQuery)
            if (anchors.any { it.isNotBlank() && (title.contains(it) || content.contains(it)) }) {
                signal += 0.12f
            }
        }
        return signal.coerceIn(0f, 0.32f)
    }

    fun matchesDefinitionSubject(understanding: QueryUnderstandingResult, normalized: String): Boolean {
        if (!understanding.signals.contains("definition_style")) return false
        val subject = QueryRewritePlanner.extractDefinitionSubject(understanding.normalizedQuery) ?: return false
        val subjectTokens = informativeTokens(subject)
        return subjectTokens.isNotEmpty() && subjectTokens.any(normalized::contains)
    }
}
