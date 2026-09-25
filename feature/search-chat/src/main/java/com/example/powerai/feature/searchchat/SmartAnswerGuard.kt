package com.example.powerai.feature.searchchat

data class SmartFastPathAnswer(
    val answer: String,
    val reason: String
)

object SmartAnswerGuard {
    private val pureGreetingPattern = Regex(
        pattern = """^(你好|您好|嗨|哈喽|hello|hi|hey)[!?.，！？\\s]*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val reasoningLeadStarts = listOf(
        "接下来",
        "先看：",
        "先看",
        "考虑到用户的问题",
        "用户发来的是",
        "最后，我要确保",
        "推理过程",
        "我需要",
        "让我想想",
        "看起来像是"
    )

    private val reasoningLeadMarkers = listOf(
        "用户的问题",
        "用户发来",
        "用户提供：",
        "格式要求",
        "回答必须",
        "不要复述问题",
        "从第一个字开始",
        "推理过程",
        "思考过程",
        "检索到本地资料",
        "未检索到本地资料支持",
        "系统在检查"
    )

    fun resolveFastPath(query: String): SmartFastPathAnswer? {
        val normalized = query.trim()
        if (pureGreetingPattern.matches(normalized)) {
            return SmartFastPathAnswer(
                answer = "你好，我可以基于本地资料帮你查询规则、定义和依据。",
                reason = "问候语快捷直答"
            )
        }
        return null
    }

    fun isMetaReasoningLead(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        if (reasoningLeadStarts.any { normalized.startsWith(it) }) return true
        val hitCount = reasoningLeadMarkers.count { normalized.contains(it) }
        return hitCount >= 2
    }

    fun stripMetaReasoningLead(text: String): String {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return normalized

        val paragraphs = normalized
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return ""

        val trimmedParagraphs = paragraphs.dropWhile(::isMetaReasoningLead)
        return if (trimmedParagraphs.size != paragraphs.size && trimmedParagraphs.isNotEmpty()) {
            trimmedParagraphs.joinToString("\n\n")
        } else {
            normalized
        }
    }

    private fun normalize(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
