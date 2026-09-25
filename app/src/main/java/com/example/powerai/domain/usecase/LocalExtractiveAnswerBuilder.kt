package com.example.powerai.domain.usecase

import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.query.QueryIntent
import com.example.powerai.domain.query.QueryRewritePlanner
import com.example.powerai.domain.query.QueryUnderstandingPipeline
import com.example.powerai.util.PdfSourceRef
import java.util.Locale

data class LocalExtractiveAnswer(
    val text: String,
    val sourceLabel: String,
    val score: Float,
    val retrievalId: Long? = null
) {
    fun toDisplayText(): String {
        return buildString {
            append("> ")
            append(text)
            if (sourceLabel.isNotBlank()) {
                append("\n\n依据")
                append(sourceLabel)
            }
        }
    }
}

internal object LocalExtractiveAnswerBuilder {
        val stopMarkers = listOf("停止运行", "停运", "应立即停止运行")
    private val normativeMarkers = listOf("", "必须", "不得", "不准", "严禁", "", "", "", "然后")

    fun extract(
        query: String,
        retrievals: List<RetrievalResult>,
        assessment: LocalEvidenceAssessment
    ): LocalExtractiveAnswer? {
        if (retrievals.isEmpty()) return null
        if (assessment.decision == LocalAnswerDecision.INSUFFICIENT_EVIDENCE && assessment.exactMatchCount == 0) {
            return null
        }

        val understanding = QueryUnderstandingPipeline.understand(query)
        val candidates = retrievals.take(3).flatMap { result ->
            buildCandidates(understanding.intent, understanding.retrievalQueries, result)
        }
        val best = candidates.maxByOrNull { it.score } ?: return null
        if (best.score < 0.42f) return null

        return LocalExtractiveAnswer(
            text = best.text,
            sourceLabel = best.sourceLabel,
            score = best.score,
            retrievalId = best.retrievalId
        )
    }

    private fun buildCandidates(
        intent: QueryIntent,
        retrievalQueries: List<String>,
        result: RetrievalResult
    ): List<Candidate> {
        val title = result.item?.title ?: result.metadata["title"].orEmpty()
        val content = result.item?.content ?: result.metadata["snippet"].orEmpty()
        val source = result.item?.source ?: result.metadata["source"].orEmpty()
        val article = extractArticleSignature(title, content)
        val sourceLabel = buildReadableSourceLabel(
            title = title,
            source = source,
            article = article
        )

        val sentences = splitSentences(content)
        val baseScore = (result.confidence ?: result.score).coerceIn(0f, 1f)
        val candidates = mutableListOf<Candidate>()

        sentences.forEachIndexed { index, sentence ->
            val contextText = neighboringWindow(sentences, index)
            val score = scoreSentence(
                intent = intent,
                retrievalQueries = retrievalQueries,
                title = title,
                source = source,
                article = article,
                sentence = sentence,
                contextualWindow = contextText,
                baseScore = baseScore
            )
            if (score > 0f) {
                candidates += Candidate(
                    text = contextText,
                    sourceLabel = sourceLabel.ifBlank { article },
                    score = score,
                    retrievalId = result.item?.id ?: result.id
                )
            }
        }

        if (candidates.isNotEmpty()) return candidates

        if (content.isBlank()) return emptyList()
        val fallbackText = content.replace('\n', ' ').trim().take(160)
        if (fallbackText.isBlank()) return emptyList()

        return listOf(
            Candidate(
                text = fallbackText,
                sourceLabel = sourceLabel.ifBlank { article },
                score = (baseScore * 0.7f).coerceIn(0f, 1f),
                retrievalId = result.item?.id ?: result.id
            )
        )
    }

    private fun scoreSentence(
        intent: QueryIntent,
        retrievalQueries: List<String>,
        title: String,
        source: String,
        article: String,
        sentence: String,
        contextualWindow: String,
        baseScore: Float
    ): Float {
        val normalizedSentence = TextSanitizer.normalizeForSearch(sentence)
        if (normalizedSentence.isBlank()) return 0f

        val tokenGroups = retrievalQueries
            .ifEmpty { listOf(sentence) }
            .map(::informativeTokens)
            .filter { it.isNotEmpty() }
        val tokenCoverage = tokenGroups.maxOfOrNull { tokens ->
            tokens.count(normalizedSentence::contains).toFloat() / tokens.size.toFloat()
        } ?: 0f
        val anchorHits = anchorsFor(intent, retrievalQueries.joinToString(" "))
            .count { anchor -> anchor.isNotBlank() && normalizedSentence.contains(anchor) }
        val normativeBonus = if (normativeMarkers.any(sentence::contains)) 0.14f else 0f
        val articleBonus = if (article.isNotBlank()) 0.08f else 0f
        val sourceBonus = if (source.contains("规程") || source.contains("规则") || source.contains("规范")) 0.08f else 0f
        val titleBonus = if (title.isNotBlank() && informativeTokens(title).any(normalizedSentence::contains)) 0.06f else 0f
        val lengthPenalty = if (sentence.length < 10) 0.10f else 0f
        val contextBonus = if (contextualWindow != sentence) 0.04f else 0f

        var score = baseScore * 0.32f
        score += tokenCoverage * 0.34f
        score += anchorHits.coerceAtMost(2) * 0.10f
        score += normativeBonus
        score += articleBonus
        score += sourceBonus
        score += titleBonus
        score += contextBonus
        score -= lengthPenalty

        return score.coerceIn(0f, 1f)
    }

    private fun neighboringWindow(sentences: List<String>, index: Int): String {
        val current = sentences.getOrNull(index).orEmpty().trim()
        val next = sentences.getOrNull(index + 1).orEmpty().trim()
        if (current.isBlank()) return next
        if (next.isBlank()) return current
        if (current.length >= 70) return current
        return "$current $next".trim()
    }

    private fun splitSentences(content: String): List<String> {
        return Regex("[^。！？；\n]+[。！？；]?")
            .findAll(content)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun informativeTokens(text: String): List<String> {
        return TextSanitizer.normalizeForSearch(text)
            .split(' ')
            .filter { token -> token.isNotBlank() && token.length >= 2 && token !in LocalEvidenceHeuristics.lowSignalTokens }
            .distinct()
    }

    private fun anchorsFor(intent: QueryIntent, query: String): List<String> = when (intent) {
        QueryIntent.PROCEDURE -> listOf("首先", "其次", "然后", "最")
        QueryIntent.COMPARISON -> listOf("区别", "不同", "差异", "分别")
        QueryIntent.TOPIC_OVERVIEW -> informativeTokens(query).take(2)
        QueryIntent.FACT_QUESTION -> when {
            QueryRewritePlanner.isActionRuleStyleQuery(query) -> QueryRewritePlanner.extractActionAnchors(query)
            else -> informativeTokens(query).take(3)
        }
    }

    private fun extractArticleSignature(title: String, content: String): String {
        val match = Regex("([一二三四五六七八九十百0-9]+)").find("$title $content") ?: return ""
        return match.value
    }

    private fun buildReadableSourceLabel(
        title: String,
        source: String,
        article: String
    ): String {
        val cleanSource = PdfSourceRef.userVisibleSource(source)
        return PdfSourceRef.buildUserVisibleLabel(
            article.takeIf { it.isNotBlank() && !title.contains(it) },
            title.takeIf { it.isNotBlank() },
            cleanSource.takeIf { it.isNotBlank() && it != title }
        )
    }

    private data class Candidate(
        val text: String,
        val sourceLabel: String,
        val score: Float,
        val retrievalId: Long?
    )
}
