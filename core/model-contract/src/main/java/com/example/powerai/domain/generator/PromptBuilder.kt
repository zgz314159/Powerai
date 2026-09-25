package com.example.powerai.domain.generator

import com.example.powerai.core.model.RetrievalResult

/**
 * Simple PromptBuilder that assembles evidence blocks into a prompt under a token budget.
 */
class PromptBuilder(
    private val tokenBudget: Int = 2000,
    private val safetyMargin: Int = 200
) {

    data class Prompt(val system: String, val user: String)

    enum class LocalPromptMode {
        FACT_ANSWER,
        TOPIC_OVERVIEW,
        PROCEDURE_GUIDE,
        COMPARISON_SUMMARY
    }

    private val systemPrompt = """
        你是一个基于本地文档的专业助手。基于下面提供的证据回答用户问题，回答要精炼、引用来源并在不确定时明确指出不确定性
        每条引用格式[来源: {source} | id:{id} | conf:{confidence}]
        如果证据带有 [精准匹配] 标记，请在回答时优先引用其具体条ID
    """.trimIndent()

    class CharacterCountTokenEstimator(private val factor: Double = 0.85) {
        fun estimate(text: String): Int {
            if (text.isBlank()) return 0
            val len = text.length
            val t = Math.ceil(len * factor).toInt()
            return if (t <= 0) 1 else t
        }
    }

    fun buildPrompt(question: String, results: List<RetrievalResult>): Prompt {
        val (priority, rest) = results.partition { it.debug?.get("fts_bonus_applied") == true }
        val sortedRest = rest.sortedByDescending { it.confidence ?: it.score }
        val ordered = ArrayList<RetrievalResult>().apply {
            addAll(priority)
            addAll(sortedRest)
        }

        val builder = StringBuilder()
        var usedTokens = 0
        val estimator = CharacterCountTokenEstimator()

        for (rr in ordered) {
            val conf = (rr.confidence ?: rr.score).toString()
            val id = rr.id?.toString() ?: rr.metadata["docId"] ?: "unknown"
            val source = rr.metadata["source"] ?: rr.source.ifBlank { "local" }

            val star = if (rr.debug?.get("fts_bonus_applied") == true) "[精准匹配] " else ""

            val title = rr.item?.title ?: rr.metadata["title"] ?: "(no title)"
            val content = rr.item?.content ?: rr.metadata["snippet"] ?: ""

            val header = "$star[来源: $source | id:$id | conf:$conf] $title\n"
            val evidence = header + (content.ifBlank { "(no content)" }) + "\n\n"

            val tokens = estimator.estimate(evidence)
            if (usedTokens + tokens + safetyMargin > tokenBudget) break
            builder.append(evidence)
            usedTokens += tokens
        }

        val userPart = StringBuilder()
        userPart.append("问题: ").append(question).append("\n\n")
        userPart.append("证据:\n").append(builder.toString())
        userPart.append("\n请基于上述证据回答问题，回答要精炼并列出最多 3 个引用。")

        return Prompt(system = systemPrompt, user = userPart.toString())
    }

    fun buildLocalGroundedPrompt(
        question: String,
        results: List<RetrievalResult>,
        mode: LocalPromptMode
    ): Prompt {
        // reuse buildPrompt logic but could specialize system prompt based on mode
        return buildPrompt(question, results)
    }
}
