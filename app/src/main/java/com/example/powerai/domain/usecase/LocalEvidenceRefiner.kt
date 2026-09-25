package com.example.powerai.domain.usecase

import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.domain.util.ContextualSearchTextBuilder
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.query.QueryIntent
import com.example.powerai.domain.query.QueryUnderstandingPipeline
import java.util.Locale

internal object LocalEvidenceRefiner {

    fun refine(query: String, retrievals: List<RetrievalResult>, displayLimit: Int = 3): LocalEvidenceRefinement {
        if (retrievals.isEmpty()) {
            return LocalEvidenceRefinement(emptyList(), 0, 0, 0, emptyList())
        }

        val understanding = QueryUnderstandingPipeline.understand(query)
        val evaluations = retrievals.mapNotNull { evaluateCandidate(understanding, it) }
        val scored = evaluations.mapNotNull { it.kept }
        val discarded = evaluations.mapNotNull { it.discarded }.sortedByDescending { it.tokenCoverage.toFloatOrNull() ?: 0f }

        val deduped = deduplicate(scored, understanding)
        val selected = deduped.sortedByDescending { it.confidence ?: it.score }.take(displayLimit.coerceAtLeast(1))

        return LocalEvidenceRefinement(
            retrievals = selected,
            totalCandidateCount = retrievals.size,
            refinedCandidateCount = deduped.size,
            discardedCandidateCount = (retrievals.size - selected.size).coerceAtLeast(0),
            discardedCandidates = discarded.take(3)
        )
    }

    private data class CandidateEvaluation(val kept: RetrievalResult?, val discarded: LocalDiscardedCandidate?)

    private fun evaluateCandidate(
        understanding: com.example.powerai.domain.query.QueryUnderstandingResult,
        result: RetrievalResult
    ): CandidateEvaluation? {
        val title = result.item?.title ?: result.metadata["title"].orEmpty()
        val content = result.item?.content ?: result.metadata["snippet"].orEmpty()
        val matchedFigureLabel = (result.debug?.get("semantic_matched_figure_label") as? String) ?: result.metadata["semantic_matched_figure_label"].orEmpty()
        val matchedFigureCaption = (result.debug?.get("semantic_matched_figure_caption") as? String) ?: result.metadata["semantic_matched_figure_caption"].orEmpty()

        val normalized = TextSanitizer.normalizeForSearch("$title $content $matchedFigureLabel $matchedFigureCaption")
        if (normalized.isBlank()) return null

        val tokenGroups = LocalEvidenceScorer.informativeTokenGroups(understanding)
        val tokenCoverage = tokenGroups.map { tokens -> tokens.count(normalized::contains).toFloat() / tokens.size.toFloat() }.maxOrNull() ?: 0f

        val anchors = LocalEvidenceScorer.anchorsFor(understanding.intent, understanding.normalizedQuery)
        val hasAnchor = anchors.any(normalized::contains)

        val exactLike = LocalEvidenceHeuristics.isExactLike(understanding.normalizedQuery, result, normalized)
        val questionStyle = LocalEvidenceHeuristics.isQuestionStyleAnswer(title, content)
        val qaStyle = LocalEvidenceHeuristics.isHandbookQaEntry(title, content)
        val noisy = LocalEvidenceHeuristics.noisyMarkers.any(normalized::contains)

        val contextualSignal = LocalEvidenceScorer.contextualSignal(title, content, result, understanding)
        val definitionSubjectMatched = LocalEvidenceScorer.matchesDefinitionSubject(understanding, normalized)

        val keep = when (understanding.intent) {
            QueryIntent.TOPIC_OVERVIEW -> tokenCoverage >= 0.34f
            QueryIntent.PROCEDURE -> exactLike || ((tokenCoverage + contextualSignal) >= 0.5f && (hasAnchor || questionStyle))
            QueryIntent.COMPARISON -> exactLike || ((tokenCoverage + contextualSignal) >= 0.34f && (hasAnchor || normalized.contains("区别")))
            QueryIntent.FACT_QUESTION -> {
                if (understanding.signals.contains("condition_style")) {
                    exactLike || ((tokenCoverage + contextualSignal) >= 0.5f && hasAnchor)
                } else if (understanding.signals.contains("action_rule_style")) {
                    exactLike || ((tokenCoverage + contextualSignal) >= 0.5f && hasAnchor)
                } else if (understanding.signals.contains("definition_style")) {
                    // 主体匹配证据：问答/问题风格，或规则正文本身在讨论该主体（而非仅标题提及）。
                    val bodySubjectMatched = LocalEvidenceScorer.matchesDefinitionSubject(
                        understanding,
                        TextSanitizer.normalizeForSearch(content)
                    )
                    exactLike || (
                        definitionSubjectMatched &&
                            (tokenCoverage + contextualSignal) >= 0.45f &&
                            (hasAnchor || questionStyle || qaStyle || bodySubjectMatched)
                    )
                } else {
                    exactLike || (tokenCoverage + contextualSignal) >= 0.5f
                }
            }
        }

        if (!keep) {
            val explanation = LocalEvidenceExplanationBuilder.buildDiscardExplanation(understanding, normalized, anchors, hasAnchor, questionStyle, qaStyle, noisy, tokenCoverage, contextualSignal)
            return CandidateEvaluation(null, LocalDiscardedCandidate(title.ifBlank { "(无标" }, result.item?.source ?: result.source, explanation.reason, explanation.detailCode, explanation.detail, LocalEvidenceExplanationBuilder.buildThresholdSnapshot(understanding, tokenCoverage, contextualSignal, if (hasAnchor) 1 else 0, hasAnchor, noisy, questionStyle, qaStyle), String.format(Locale.US, "%.2f", tokenCoverage), String.format(Locale.US, "%.2f", contextualSignal)))
        }

        var rerankScore = (result.confidence ?: result.score).coerceIn(0f, 1f)
        rerankScore += tokenCoverage * 0.28f + contextualSignal * 0.18f + (if (hasAnchor) 0.12f else 0f)
        if (exactLike) rerankScore += 0.24f

        val debug = (result.debug?.toMutableMap() ?: mutableMapOf()).apply {
            this["post_filter_passed"] = true
            this["rerank_score"] = String.format(Locale.US, "%.2f", rerankScore.coerceIn(0f, 1f))
            this["token_coverage"] = String.format(Locale.US, "%.2f", tokenCoverage)
            this["contextual_signal"] = String.format(Locale.US, "%.2f", contextualSignal)
            if (!containsKey("context_label")) {
                val contextLabel = result.item?.contextLabel?.takeIf { it.isNotBlank() }
                    ?: ContextualSearchTextBuilder.buildContextLabel(
                        title = title,
                        source = result.item?.source ?: result.source,
                        category = result.item?.category.orEmpty(),
                        pageNumber = result.item?.pageNumber
                    ).ifBlank { result.item?.source ?: result.source }
                        .ifBlank { title }
                        .ifBlank { content }
                this["context_label"] = contextLabel
            }
        }

        val finalScore = rerankScore.coerceIn(0f, 1f)
        return CandidateEvaluation(result.copy(score = finalScore, confidence = finalScore, debug = debug), null)
    }

    private fun deduplicate(retrievals: List<RetrievalResult>, understanding: com.example.powerai.domain.query.QueryUnderstandingResult): List<RetrievalResult> {
        val grouped = linkedMapOf<String, RetrievalResult>()
        for (result in retrievals) {
            val key = clusterKey(understanding, result)
            val existing = grouped[key]
            if (existing == null || (result.confidence ?: result.score) > (existing.confidence ?: existing.score)) {
                grouped[key] = result
            }
        }
        return grouped.values.toList()
    }

    private fun clusterKey(understanding: com.example.powerai.domain.query.QueryUnderstandingResult, result: RetrievalResult): String {
        val title = result.item?.title ?: result.metadata["title"].orEmpty()
        val content = result.item?.content ?: result.metadata["snippet"].orEmpty()
        val article = LocalEvidenceHeuristics.extractArticleSignature(title, content)
        return if (article.isNotBlank()) article else TextSanitizer.normalizeForSearch("$title $content").take(48)
    }
}
