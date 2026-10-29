package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import kotlin.math.abs

/**
 * 对本地检索结果做轻量重排/过滤：
 * - 问题含明确电压等级时，优先同等级，丢掉串压
 * - 「如何/怎样」类操作问，压低表格噪声，优先规程条文
 * - **相关度门闸**：与问题主题词重合不足的证据整条丢弃（宁可 0 条，也不把手套塞进变压器题）
 */
object SmartEvidenceRanker {
    private val kvPattern =
        Regex(
            """(\d+(?:\.\d+)?)\s*[kK][vV]|(\d+(?:\.\d+)?)\s*千伏""",
        )

    private val procedurePattern = Regex("如何|怎样|怎么|步骤|要领|操作")

    /** 领域短语优先于 bigram，避免「绝缘」单独把手套配到「绝缘电阻」题上。 */
    private val domainPhrases =
        listOf(
            "绝缘电阻",
            "接地电阻",
            "绝缘手套",
            "绝缘靴",
            "绝缘鞋",
            "验电器",
            "验电笔",
            "工作票",
            "操作票",
            "隔离开关",
            "断路器",
            "变压器",
            "电动机",
            "发电机",
            "开关柜",
            "母线",
            "电缆",
            "架空",
            "倒闸",
            "接地线",
            "验电",
            "接地",
            "继电保护",
            "保护定值",
        )

    private val stopTokens =
        setOf(
            "如何",
            "怎样",
            "怎么",
            "什么",
            "多大",
            "多少",
            "是否",
            "可以",
            "应该",
            "需要",
            "进行",
            "使用",
            "要求",
            "规定",
            "请问",
            "一下",
            "相关",
            "问题",
            "答案",
            "的是",
            "是多",
            "有多",
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

    fun isTableLike(item: KnowledgeItem): Boolean {
        val title = item.title
        val content = item.content.trimStart()
        return title.contains("表格") ||
            title.contains("表#") ||
            content.startsWith("|") ||
            content.contains("|---|")
    }

    /** 从问题抽出用于相关度判断的主题词（短语 > 汉字 bigram > 拉丁词）。 */
    fun extractQueryTerms(question: String): Set<String> {
        val q = question.trim()
        if (q.isBlank()) return emptySet()
        val terms = linkedSetOf<String>()
        for (phrase in domainPhrases) {
            if (q.contains(phrase)) terms += phrase
        }
        val han = q.filter { it.code in 0x4E00..0x9FFF }
        if (han.length >= 2) {
            for (i in 0 until han.length - 1) {
                val bi = han.substring(i, i + 2)
                if (bi in stopTokens) continue
                // 已被更长短语覆盖则跳过（如「绝缘电阻」已收录则不再单独要「绝缘」「电阻」）
                if (terms.any { it.length >= 3 && it.contains(bi) }) continue
                terms += bi
            }
        }
        Regex("""[A-Za-z][A-Za-z0-9]*|\d+(?:\.\d+)?\s*[kK][vV]""").findAll(q).forEach {
            val t = it.value.replace(" ", "").lowercase()
            if (t.length >= 2) terms += t
        }
        return terms
    }

    /**
     * 证据是否足以支撑该问。
     * - 主题词 ≥2：至少命中 2 个，或命中率 ≥ 0.5
     * - 主题词 =1：必须命中该词
     * - 抽不出主题词：放行（避免误杀）
     */
    fun isRelevantToQuestion(
        question: String,
        item: KnowledgeItem,
    ): Boolean {
        val terms = extractQueryTerms(question)
        if (terms.isEmpty()) return true
        val hay =
            buildString {
                append(item.title)
                append(' ')
                append(item.content.take(800))
            }.lowercase()
        val hits = terms.count { term -> hay.contains(term.lowercase()) }
        return when {
            terms.size == 1 -> hits >= 1
            else -> hits >= 2 || hits.toFloat() / terms.size.toFloat() >= 0.5f
        }
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
                if (isRelevantToQuestion(question, item)) {
                    score += 2
                } else {
                    score -= 8
                }
                Scored(item, index, score)
            }

        val relevant =
            scored.filter { it.score >= 0 && isRelevantToQuestion(question, it.item) }
        if (relevant.isEmpty()) {
            // 门闸：无一足够相关 → 空列表，交由「资料不足」/联网，禁止脏证据进 Prompt。
            return emptyList()
        }
        val positive = relevant.filter { it.score >= 0 }
        val pool =
            when {
                positive.any { it.score >= 4 } -> positive
                positive.isNotEmpty() -> positive
                else -> relevant
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
