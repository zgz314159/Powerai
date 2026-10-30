package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem

/**
 * 规程关键断言的轻量结构核验（非语义蕴含模型）。
 * 拦已知职责/顺序改写；拦不住的不得宣称已证明蕴含。
 */
object SmartSemanticClaimGuard {
    private val dutyPair = Regex("一人操作[,，]?一人监护|一人操作.{0,4}一人监护")
    private val dutyRewrite =
        listOf(
            Regex("两人?轮流"),
            Regex("轮流操作"),
            Regex("轮流进行"),
            Regex("交替操作"),
            Regex("互相操作"),
        )

    private val orderInvent =
        listOf(
            Regex("首先"),
            Regex("其次"),
            Regex("然后"),
            Regex("接着"),
            Regex("最后"),
            Regex("第一步"),
            Regex("第二步"),
            Regex("第三步"),
        )

    /**
     * @return 违规原因码；null 表示未发现已知结构错误。
     */
    fun findRewriteViolation(
        answer: String,
        references: List<KnowledgeItem>,
    ): String? {
        if (answer.isBlank() || references.isEmpty()) return null
        val hay = references.joinToString("\n") { "${it.title}\n${it.content}" }
        val a = answer.replace("\r\n", "\n")

        if (dutyPair.containsMatchIn(hay) && dutyRewrite.any { it.containsMatchIn(a) }) {
            return "DUTY_REWRITE"
        }
        // 答案发明顺序词，而证据中完全没有对应顺序表述
        val invented =
            orderInvent.filter { cue ->
                cue.containsMatchIn(a) && !cue.containsMatchIn(hay)
            }
        if (invented.size >= 2) {
            return "ORDER_INVENT"
        }
        // 否定词翻转：证据「不得/不准/严禁」却被答成「可以/允许」同对象（轻量）
        if (Regex("不得|不准|严禁").containsMatchIn(hay) &&
            Regex("可以(?:直接)?|允许|不必").containsMatchIn(a) &&
            !Regex("可以(?:直接)?|允许|不必").containsMatchIn(hay)
        ) {
            // 仅当答案同时提到证据中的核心动作词时才判
            val actionHints = listOf("操作", "作业", "停电", "验电", "接地")
            if (actionHints.any { hay.contains(it) && a.contains(it) }) {
                return "NEGATION_FLIP"
            }
        }
        return null
    }

    fun hasRewriteViolation(
        answer: String,
        references: List<KnowledgeItem>,
    ): Boolean = findRewriteViolation(answer, references) != null
}
