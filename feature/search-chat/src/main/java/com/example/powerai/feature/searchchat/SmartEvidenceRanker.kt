package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import kotlin.math.abs

/**
 * 对本地检索结果做轻量重排/过滤：
 * - 问题含明确电压等级时，优先同等级，丢掉串压
 * - 「如何/怎样」类操作问，压低表格噪声，优先规程条文
 */
object SmartEvidenceRanker {
    private val kvPattern =
        Regex(
            """(\d+(?:\.\d+)?)\s*[kK][vV]|(\d+(?:\.\d+)?)\s*千伏""",
        )

    private val procedurePattern = Regex("如何|怎样|怎么|步骤|要领|操作")

    fun extractKvLevels(text: String): Set<Double> {
        if (text.isBlank()) return emptySet()
        return kvPattern.findAll(text)
            .mapNotNull { match ->
                val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
                raw.toDoubleOrNull()
            }
            .toSet()
    }

    fun isTableLike(item: KnowledgeItem): Boolean {
        val title = item.title
        val content = item.content.trimStart()
        return title.contains("表格") ||
            title.contains("表#") ||
            content.startsWith("|") ||
            content.contains("|---|")
    }

    fun refine(
        question: String,
        items: List<KnowledgeItem>,
        limit: Int,
    ): List<KnowledgeItem> {
        if (items.isEmpty() || limit <= 0) return emptyList()
        val queryLevels = extractKvLevels(question)
        val procedureAsk = procedurePattern.containsMatchIn(question)

        data class Scored(val item: KnowledgeItem, val order: Int, val score: Int)

        val scored =
            items.mapIndexed { index, item ->
                val hay =
                    buildString {
                        append(item.title)
                        append(' ')
                        append(item.content.take(500))
                    }
                val itemLevels = extractKvLevels(hay)
                var score =
                    when {
                        queryLevels.isEmpty() -> 0
                        itemLevels.isEmpty() -> 1
                        itemLevels.any { level -> queryLevels.any { q -> sameKv(q, level) } } -> 4
                        else -> -3 // 明确串压
                    }
                if (procedureAsk && isTableLike(item)) {
                    score -= 3
                }
                if (procedureAsk && !isTableLike(item) && (item.title.contains("验电") || hay.contains("验电"))) {
                    score += 2
                }
                Scored(item, index, score)
            }

        val positive = scored.filter { it.score >= 0 }
        val pool =
            when {
                positive.any { it.score >= 4 } -> positive
                positive.isNotEmpty() -> positive
                else -> scored
            }
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
