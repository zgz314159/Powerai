package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem

/**
 * CRAG 式检索置信度：Correct / Ambiguous / Incorrect。
 * 端侧用规则打分（不另调 LLM），重点看「方面词」是否被证据覆盖，避免单实体弱命中硬答。
 */
enum class SmartEvidenceConfidence {
    /** 至少一个证据能较好支撑问题方面。 */
    CORRECT,

    /** 有词面重合但方面覆盖弱。 */
    AMBIGUOUS,

    /** 无可用证据或完全不相关。 */
    INCORRECT,
}

object SmartEvidenceGrader {
    private val aspectPhrases =
        listOf(
            "绝缘电阻",
            "接地电阻",
            "安全距离",
            "最小距离",
            "电压偏差",
            "允许偏差",
            "验电",
            "接地",
            "温升",
            "绝缘手套",
            "工作票",
            "倒闸",
            "保护定值",
        )

    private val entityPhrases =
        listOf(
            "变压器",
            "电动机",
            "发电机",
            "隔离开关",
            "断路器",
            "开关柜",
            "母线",
            "电缆",
            "杆塔",
            "避雷器",
        )

    fun extractAspects(question: String): Set<String> =
        aspectPhrases.filter { question.contains(it) }.toSet()

    fun extractEntities(question: String): Set<String> =
        entityPhrases.filter { question.contains(it) }.toSet()

    /**
     * 单条证据对问题的可答性分 [0,1]。
     * 有方面词时：方面命中权重大；仅实体命中则偏低。
     */
    fun answerabilityScore(
        question: String,
        item: KnowledgeItem,
    ): Float {
        val hay = "${item.title}\n${item.content.take(900)}"
        val entities = extractEntities(question)
        val aspects = extractAspects(question)
        var score = 0f

        if (entities.isNotEmpty()) {
            val hits = entities.count { hay.contains(it) }
            score += hits.toFloat() / entities.size * 0.35f
            if (entities.any { item.title.contains(it) }) score += 0.1f
        } else {
            // 无明确实体：退回主题词重合
            val terms = SmartEvidenceRanker.extractQueryTerms(question)
            if (terms.isNotEmpty()) {
                val hits = terms.count { hay.contains(it, ignoreCase = true) }
                score += hits.toFloat() / terms.size * 0.35f
            }
        }

        if (aspects.isNotEmpty()) {
            val hits = aspects.count { hay.contains(it) }
            score += hits.toFloat() / aspects.size * 0.55f
        } else {
            // 问题无方面词（本不应到达此处，歧义门闸应拦住）：实体命中也只给模糊分
            score *= 0.45f
        }

        return score.coerceIn(0f, 1f)
    }

    fun grade(
        question: String,
        items: List<KnowledgeItem>,
    ): SmartEvidenceConfidence {
        if (items.isEmpty()) return SmartEvidenceConfidence.INCORRECT
        val best = items.maxOf { answerabilityScore(question, it) }
        return when {
            best >= 0.55f -> SmartEvidenceConfidence.CORRECT
            best >= 0.28f -> SmartEvidenceConfidence.AMBIGUOUS
            else -> SmartEvidenceConfidence.INCORRECT
        }
    }

    /** Incorrect 清空；Ambiguous/Correct 保留（Ambiguous 由上层决定是否联网）。 */
    fun filterByGrade(
        question: String,
        items: List<KnowledgeItem>,
        grade: SmartEvidenceConfidence,
    ): List<KnowledgeItem> {
        return when (grade) {
            SmartEvidenceConfidence.INCORRECT -> emptyList()
            SmartEvidenceConfidence.AMBIGUOUS,
            SmartEvidenceConfidence.CORRECT,
            ->
                items.filter { answerabilityScore(question, it) >= 0.28f }
                    .ifEmpty { emptyList() }
        }
    }
}
