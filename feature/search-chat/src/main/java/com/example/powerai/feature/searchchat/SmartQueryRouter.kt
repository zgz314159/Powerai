package com.example.powerai.feature.searchchat

/**
 * 智能页问题分流：决定是否自动联网、是否依赖本地规程证据。
 * 规则偏保守——电力规程默认仅本地；时效/新闻在本地 0 命中时才考虑网页。
 * 数值/标准类在本地相关证据为空时也允许补网页（避免脏本地硬答）。
 */
enum class SmartQueryKind {
    /** 安规/规程/操作类：优先本地 KB。 */
    DOMAIN,

    /** 日期/新闻/「最新」等时效问题。 */
    TEMPORAL,

    /** 寒暄或开放闲聊。 */
    CHITCHAT,
}

object SmartQueryRouter {
    private val temporalPatterns =
        listOf(
            Regex("今天几[号日]"),
            Regex("现在几[点号日]"),
            Regex("几月几[号日]"),
            Regex("当前日期|今日日期|系统时间"),
            Regex("新闻|头条|热点"),
            Regex("最新|近日|本周|本月|今年"),
            Regex("天气"),
        )

    private val domainPatterns =
        listOf(
            Regex("安规|规程|验电|接地|绝缘|倒闸|操作票|两票"),
            Regex("继电|保护定值|开关柜|变压器|母线|电缆|架空"),
            Regex("停电|送电|挂接地|摘接地|有电|无电"),
            Regex("kV|千伏|兆瓦"),
        )

    private val chitchatPatterns =
        listOf(
            Regex("^你好"),
            Regex("你好吗"),
            Regex("你是谁"),
            Regex("能联网吗|在吗"),
        )

    private val factualNumericPattern =
        Regex("多大|多少|几欧|阻值|标准值|允许值|最小值|不小于|不低于|MΩ|mΩ|兆欧")

    fun classify(question: String): SmartQueryKind {
        val q = question.trim()
        if (q.isEmpty()) return SmartQueryKind.DOMAIN
        if (temporalPatterns.any { it.containsMatchIn(q) }) return SmartQueryKind.TEMPORAL
        if (chitchatPatterns.any { it.containsMatchIn(q) }) return SmartQueryKind.CHITCHAT
        if (domainPatterns.any { it.containsMatchIn(q) }) return SmartQueryKind.DOMAIN
        // 默认按领域问答处理：电力助手主路径仍走本地 KB。
        return SmartQueryKind.DOMAIN
    }

    /** 数值/限值类：本地无相关证据时应允许联网补强。 */
    fun isFactualNumericAsk(question: String): Boolean =
        factualNumericPattern.containsMatchIn(question.trim())

    /**
     * 是否在「联网」开关打开且 Serper 已配置时发起网页检索。
     * - 手动联网开 + 本地相关命中为 0
     * - 或时效类问题
     * - 数值/标准类且本地相关为 0（与 DOMAIN 空命中同路径，显式便于单测）
     */
    fun shouldFetchWeb(
        kind: SmartQueryKind,
        webSearchEnabled: Boolean,
        localHitCount: Int,
        question: String = "",
    ): Boolean {
        if (!webSearchEnabled) return false
        return when (kind) {
            SmartQueryKind.TEMPORAL -> true
            SmartQueryKind.DOMAIN ->
                localHitCount == 0 ||
                    (isFactualNumericAsk(question) && localHitCount == 0)
            SmartQueryKind.CHITCHAT -> localHitCount == 0
        }
    }
}
