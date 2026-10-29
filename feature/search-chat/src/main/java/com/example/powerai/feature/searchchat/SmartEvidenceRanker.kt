package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import kotlin.math.abs

/**
 * 对本地检索结果做轻量重排/过滤：问题含明确电压等级时，优先保留同等级证据，丢掉明显串压条目。
 */
object SmartEvidenceRanker {
    private val kvPattern =
        Regex(
            """(\d+(?:\.\d+)?)\s*[kK][vV]|(\d+(?:\.\d+)?)\s*千伏""",
        )

    fun extractKvLevels(text: String): Set<Double> {
        if (text.isBlank()) return emptySet()
        return kvPattern.findAll(text)
            .mapNotNull { match ->
                val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
                raw.toDoubleOrNull()
            }
            .toSet()
    }

    fun refine(
        question: String,
        items: List<KnowledgeItem>,
        limit: Int,
    ): List<KnowledgeItem> {
        if (items.isEmpty() || limit <= 0) return emptyList()
        val queryLevels = extractKvLevels(question)
        if (queryLevels.isEmpty()) return items.take(limit)

        data class Scored(val item: KnowledgeItem, val order: Int, val score: Int)

        val scored =
            items.mapIndexed { index, item ->
                val hay = buildString {
                    append(item.title)
                    append(' ')
                    append(item.content.take(500))
                }
                val itemLevels = extractKvLevels(hay)
                val score =
                    when {
                        itemLevels.isEmpty() -> 1
                        itemLevels.any { level -> queryLevels.any { q -> sameKv(q, level) } } -> 3
                        else -> -2 // 明确串压（如问 10kV 却是 221kV）
                    }
                Scored(item, index, score)
            }

        val kept = scored.filter { it.score >= 0 }
        val pool = if (kept.any { it.score >= 3 }) kept.filter { it.score >= 0 } else scored
        return pool
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.order })
            .map { it.item }
            .take(limit)
    }

    private fun sameKv(
        a: Double,
        b: Double,
    ): Boolean = abs(a - b) < 0.05
}
