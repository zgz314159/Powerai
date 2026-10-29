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
            "设备检修",
            "停电检修",
            "检修",
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
     * 有方面词时：方面命中权重大；无词典方面时用主题词命中率代理（避免「10KV设备检修」误杀）。
     */
    fun answerabilityScore(
        question: String,
        item: KnowledgeItem,
    ): Float {
        val hay = "${item.title}\n${item.content.take(900)}"
        val hayLower = hay.lowercase()
        val entities = extractEntities(question)
        val aspects = extractAspects(question)
        val terms = SmartEvidenceRanker.extractQueryTerms(question)
        val termHitRatio =
            if (terms.isEmpty()) {
                0f
            } else {
                terms.count { hayLower.contains(it.lowercase()) }.toFloat() / terms.size
            }
        var score = 0f

        if (entities.isNotEmpty()) {
            val hits = entities.count { hay.contains(it) }
            score += hits.toFloat() / entities.size * 0.35f
            if (entities.any { item.title.contains(it) }) score += 0.1f
        } else {
            score += termHitRatio * 0.35f
        }

        if (aspects.isNotEmpty()) {
            // 优先更长短语（设备检修 > 检修）
            val ordered = aspects.sortedByDescending { it.length }
            val hits = ordered.count { hay.contains(it) }
            score += hits.toFloat() / ordered.size * 0.55f
        } else {
            // 无方面词典命中时：不要再乘惩罚；主题词高重合即可视为可答
            score += termHitRatio * 0.55f
        }

        // 电压等级一致加分（问 10kV、证也有 10kV）
        val qKv = SmartEvidenceRanker.extractKvLevels(question)
        val eKv = SmartEvidenceRanker.extractKvLevels(hay)
        if (qKv.isNotEmpty() && eKv.any { level -> qKv.any { q -> kotlin.math.abs(q - level) < 0.05 } }) {
            score += 0.12f
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
