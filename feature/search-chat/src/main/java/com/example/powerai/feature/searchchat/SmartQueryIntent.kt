package com.example.powerai.feature.searchchat

/**
 * 问句意图门闸（RQ-RAG / Microsoft query understanding 的轻量规则版）：
 * 过短、单实体无方面的歧义问 → 先澄清，禁止硬生成。
 */
object SmartQueryIntent {
    private val intentCuePattern =
        Regex(
            "如何|怎样|怎么|什么|多大|多少|几|是否|能否|可以|应该|要求|标准|规定|" +
                "距离|电阻|偏差|温升|步骤|要领|方法|条件|范围|限值|不小于|不低于|" +
                "检修|停电|作业|为什么|哪[些个]|何时|哪里",
        )

    private val entityHints =
        listOf(
            "变压器",
            "电动机",
            "发电机",
            "隔离开关",
            "断路器",
            "开关柜",
            "母线",
            "电缆",
            "验电器",
            "接地线",
            "工作票",
            "操作票",
        )

    private val aspectHintsByEntity =
        mapOf(
            "变压器" to
                listOf(
                    "变压器绝缘电阻要求是多少？",
                    "变压器接地电阻如何测量？",
                    "10kV变压器最小安全距离是多少？",
                ),
            "验电" to
                listOf(
                    "10kV如何验电？",
                    "高压验电有哪些安全要求？",
                ),
            "接地" to
                listOf(
                    "接地电阻要求是多少？",
                    "检修前如何接地？",
                ),
        )

    /** 汉字字数（忽略空白与标点）。 */
    fun meaningfulCharCount(question: String): Int =
        question.count { it.code in 0x4E00..0x9FFF || it.isLetterOrDigit() }

    fun hasIntentCue(question: String): Boolean = intentCuePattern.containsMatchIn(question)

    /**
     * 是否过短/歧义：不足以支撑 grounded 检索问答。
     * 例：「变压器」「验电」「接地」——缺方面词。
     * 「接地电阻」「10kV如何验电」有方面/问法 → 不算歧义。
     */
    fun isAmbiguousOrTooShort(question: String): Boolean {
        val q = question.trim()
        if (q.isBlank()) return true
        if (hasIntentCue(q)) return false
        // 仅「绝缘电阻/安全距离」等 ≥4 字方面短语算说清；单独「验电/接地」仍歧义。
        if (SmartEvidenceGrader.extractAspects(q).any { it.length >= 4 }) return false
        val normalized = q.removeSuffix("？").removeSuffix("?").trim()
        val onlyEntity = entityHints.any { hint -> normalized == hint }
        if (onlyEntity) return true
        val chars = meaningfulCharCount(q)
        // 极短且无方面/问法（如「验电」「接地」）
        return chars <= 4
    }

    fun clarificationMessage(question: String): String {
        val q = question.trim().ifBlank { "该关键词" }
        val matchedEntity = entityHints.firstOrNull { q.contains(it) }
        val examples =
            matchedEntity?.let { aspectHintsByEntity[it] }
                ?: aspectHintsByEntity.entries.firstOrNull { q.contains(it.key) }?.value
                ?: listOf(
                    "10kV如何验电？",
                    "变压器绝缘电阻多大？",
                    "10kV最小安全距离是多少？",
                )
        return buildString {
            append("问题过短或意图不清，请补充「对象 + 要问的点」后再问。\n")
            append("例如：\n")
            examples.take(3).forEach { append("· ").append(it).append('\n') }
            append("当前输入：").append(q)
        }.trimEnd()
    }
}
