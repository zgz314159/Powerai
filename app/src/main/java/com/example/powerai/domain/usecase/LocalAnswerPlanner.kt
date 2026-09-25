package com.example.powerai.domain.usecase

import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.domain.query.QueryIntent
import com.example.powerai.domain.query.QueryUnderstandingPipeline
import com.example.powerai.core.model.RetrievalResult

enum class LocalAnswerIntent {
    FACT_QUESTION,
    TOPIC_OVERVIEW,
    PROCEDURE,
    COMPARISON
}

enum class LocalAnswerDecision {
    USE_GEMMA_ANSWER,
    SHOW_EVIDENCE_ONLY,
    INSUFFICIENT_EVIDENCE
}

data class LocalEvidenceAssessment(
    val intent: LocalAnswerIntent,
    val decision: LocalAnswerDecision,
    val evidenceCount: Int,
    val promptEvidenceCount: Int,
    val primaryScore: Float,
    val aggregateScore: Float,
    val strongEvidenceCount: Int,
    val exactMatchCount: Int,
    val distinctSourceCount: Int,
    val reason: String
)

object LocalAnswerPlanner {
    private val lowSignalTokens = setOf(
        "什么", "情况", "怎么样", "哪些", "何种", "情形", "如何", "为何", "条件"
    )

    fun classifyIntent(query: String): LocalAnswerIntent {
        return QueryUnderstandingPipeline.understand(query).intent.toLocalAnswerIntent()
    }

    fun assess(query: String, retrievals: List<RetrievalResult>): LocalEvidenceAssessment {
        val intent = classifyIntent(query)
        if (retrievals.isEmpty()) {
            return LocalEvidenceAssessment(
                intent = intent,
                decision = LocalAnswerDecision.INSUFFICIENT_EVIDENCE,
                evidenceCount = 0,
                promptEvidenceCount = 0,
                primaryScore = 0f,
                aggregateScore = 0f,
                strongEvidenceCount = 0,
                exactMatchCount = 0,
                distinctSourceCount = 0,
                reason = "no_evidence"
            )
        }

        val topWindow = retrievals.take(4)
    val adjustedScores = topWindow.map { adjustedEvidenceScore(query, it) }
        val primaryScore = adjustedScores.firstOrNull() ?: 0f
        val aggregateScore = adjustedScores.average().toFloat()
        val strongEvidenceCount = adjustedScores.count { it >= 0.45f }
    val exactMatchCount = topWindow.count { isExactLikeMatch(query, it) }
        val distinctSourceCount = topWindow.mapNotNull(::resolveSource).distinct().size

        val decision = when (intent) {
            LocalAnswerIntent.FACT_QUESTION -> when {
                exactMatchCount > 0 -> LocalAnswerDecision.USE_GEMMA_ANSWER
                primaryScore >= 0.52f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                strongEvidenceCount >= 1 && aggregateScore >= 0.34f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                retrievals.size >= 2 && aggregateScore >= 0.16f -> LocalAnswerDecision.SHOW_EVIDENCE_ONLY
                else -> LocalAnswerDecision.INSUFFICIENT_EVIDENCE
            }

            LocalAnswerIntent.PROCEDURE -> when {
                exactMatchCount > 0 -> LocalAnswerDecision.USE_GEMMA_ANSWER
                primaryScore >= 0.38f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                strongEvidenceCount >= 1 && aggregateScore >= 0.30f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                retrievals.size >= 2 && aggregateScore >= 0.28f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                retrievals.size >= 2 && aggregateScore >= 0.18f -> LocalAnswerDecision.SHOW_EVIDENCE_ONLY
                else -> LocalAnswerDecision.INSUFFICIENT_EVIDENCE
            }

            LocalAnswerIntent.COMPARISON -> when {
                distinctSourceCount >= 2 && aggregateScore >= 0.22f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                retrievals.size >= 3 && aggregateScore >= 0.18f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                retrievals.size >= 2 -> LocalAnswerDecision.SHOW_EVIDENCE_ONLY
                else -> LocalAnswerDecision.INSUFFICIENT_EVIDENCE
            }

            LocalAnswerIntent.TOPIC_OVERVIEW -> when {
                retrievals.size >= 2 && (aggregateScore >= 0.24f || distinctSourceCount >= 2) -> LocalAnswerDecision.USE_GEMMA_ANSWER
                primaryScore >= 0.34f -> LocalAnswerDecision.USE_GEMMA_ANSWER
                else -> LocalAnswerDecision.SHOW_EVIDENCE_ONLY
            }
        }

        return LocalEvidenceAssessment(
            intent = intent,
            decision = decision,
            evidenceCount = retrievals.size,
            promptEvidenceCount = promptEvidenceCountFor(intent, decision, retrievals.size),
            primaryScore = primaryScore,
            aggregateScore = aggregateScore,
            strongEvidenceCount = strongEvidenceCount,
            exactMatchCount = exactMatchCount,
            distinctSourceCount = distinctSourceCount,
            reason = buildReason(intent, decision, exactMatchCount, strongEvidenceCount, aggregateScore, distinctSourceCount)
        )
    }

    fun selectPromptEvidence(
        retrievals: List<RetrievalResult>,
        assessment: LocalEvidenceAssessment
    ): List<RetrievalResult> {
        if (assessment.decision != LocalAnswerDecision.USE_GEMMA_ANSWER) return emptyList()
        return retrievals.take(assessment.promptEvidenceCount.coerceAtLeast(0))
    }

    private fun adjustedEvidenceScore(query: String, result: RetrievalResult): Float {
        val base = (result.confidence ?: result.score).coerceIn(0f, 1f)
        val content = result.item?.content ?: result.metadata["snippet"].orEmpty()
        val contentBonus = when {
            content.length >= 120 -> 0.10f
            content.length >= 60 -> 0.06f
            content.length >= 30 -> 0.03f
            else -> 0f
        }
        val exactBonus = if (isExactLikeMatch(query, result)) 0.16f else 0f
        return (base + contentBonus + exactBonus).coerceIn(0f, 1f)
    }

    private fun isExactLikeMatch(query: String, result: RetrievalResult): Boolean {
        val boosted = result.debug?.get("fts_bonus_applied") == true
        val starred = result.item?.title?.startsWith("⭐") == true
        return boosted || starred || isSemanticFactMatch(query, result)
    }

    private fun isSemanticFactMatch(query: String, result: RetrievalResult): Boolean {
        val variants = LocalSearchQueryVariantBuilder.build(query)
        if (variants.isEmpty()) return false

        val searchable = buildString {
            append(result.item?.title ?: result.metadata["title"].orEmpty())
            append(' ')
            append(result.item?.content ?: result.metadata["snippet"].orEmpty())
        }
        val normalizedSearchable = TextSanitizer.normalizeForSearch(searchable)
        if (normalizedSearchable.isBlank()) return false

        return variants.any { variant ->
            val tokens = informativeTokens(variant)
            tokens.isNotEmpty() && tokens.all(normalizedSearchable::contains)
        }
    }

    private fun informativeTokens(query: String): List<String> {
        return TextSanitizer.normalizeForSearch(query)
            .split(' ')
            .filter { token ->
                token.isNotBlank() && token.length >= 2 && token !in lowSignalTokens
            }
    }

    private fun resolveSource(result: RetrievalResult): String? {
        return result.item?.source?.takeIf { it.isNotBlank() }
            ?: result.metadata["source"]?.takeIf { it.isNotBlank() }
            ?: result.source.takeIf { it.isNotBlank() }
    }

    private fun promptEvidenceCountFor(
        intent: LocalAnswerIntent,
        decision: LocalAnswerDecision,
        evidenceCount: Int
    ): Int {
        if (decision != LocalAnswerDecision.USE_GEMMA_ANSWER) return 0
        val preferred = when (intent) {
            LocalAnswerIntent.FACT_QUESTION -> 3
            LocalAnswerIntent.TOPIC_OVERVIEW -> 4
            LocalAnswerIntent.PROCEDURE -> 4
            LocalAnswerIntent.COMPARISON -> 4
        }
        return preferred.coerceAtMost(evidenceCount)
    }

    private fun buildReason(
        intent: LocalAnswerIntent,
        decision: LocalAnswerDecision,
        exactMatchCount: Int,
        strongEvidenceCount: Int,
        aggregateScore: Float,
        distinctSourceCount: Int
    ): String = buildString {
        append(intent.name.lowercase())
        append('|')
        append(decision.name.lowercase())
        append("|exact=")
        append(exactMatchCount)
        append("|strong=")
        append(strongEvidenceCount)
        append("|agg=")
        append(String.format(java.util.Locale.US, "%.2f", aggregateScore))
        append("|sources=")
        append(distinctSourceCount)
    }

    private fun QueryIntent.toLocalAnswerIntent(): LocalAnswerIntent = when (this) {
        QueryIntent.TOPIC_OVERVIEW -> LocalAnswerIntent.TOPIC_OVERVIEW
        QueryIntent.FACT_QUESTION -> LocalAnswerIntent.FACT_QUESTION
        QueryIntent.PROCEDURE -> LocalAnswerIntent.PROCEDURE
        QueryIntent.COMPARISON -> LocalAnswerIntent.COMPARISON
    }
}
