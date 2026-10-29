package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem

data class SmartFastPathAnswer(
    val answer: String,
    val reason: String,
)

object SmartAnswerGuard {
    private val pureGreetingPattern =
        Regex(
            pattern = """^(你好|您好|嗨|哈喽|hello|hi|hey)[!?.，！？\\s]*$""",
            option = RegexOption.IGNORE_CASE,
        )

    private val reasoningLeadStarts =
        listOf(
            "接下来",
            "先看：",
            "先看",
            "考虑到用户的问题",
            "用户发来的是",
            "最后，我要确保",
            "推理过程",
            "我需要",
            "让我想想",
            "看起来像是",
        )

    private val reasoningLeadMarkers =
        listOf(
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
            "系统在检查",
            "知名用户",
            "个评分",
            "个回答",
            "要回答问题，需要根据提供的资料",
            "请根据资料分析问题",
        )

    /** 句末另起一行的残缺引用，如「[1] 10k」。 */
    private val trailingTruncatedCite =
        Regex("""^\s*\[\d+\]\s*.{0,24}\s*$""")

    fun resolveFastPath(query: String): SmartFastPathAnswer? {
        val normalized = query.trim()
        if (pureGreetingPattern.matches(normalized)) {
            return SmartFastPathAnswer(
                answer = "你好，我可以基于本地资料帮你查询规则、定义和依据。",
                reason = "问候语快捷直答",
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

        val paragraphs =
            normalized
                .split(Regex("\\n{2,}"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filterNot { it.matches(Regex("^[?？\\s]+$")) }

        if (paragraphs.isEmpty()) return ""

        val trimmedParagraphs =
            paragraphs
                .dropWhile { isMetaReasoningLead(it) || it.matches(Regex("^[?？.\\s]+$")) }
        val body =
            if (trimmedParagraphs.size != paragraphs.size && trimmedParagraphs.isNotEmpty()) {
                trimmedParagraphs.joinToString("\n\n")
            } else {
                paragraphs.joinToString("\n\n")
            }
        return stripTrailingTruncatedCites(body.replace(Regex("^\\s*[?？]+\\s*"), "").trim())
    }

    /** 去掉末尾残缺 [n] 标题行（模型常把资料标题截断再贴一次）。 */
    fun stripTrailingTruncatedCites(text: String): String {
        if (text.isBlank()) return text
        val lines = text.replace("\r\n", "\n").split('\n').toMutableList()
        while (lines.isNotEmpty()) {
            val last = lines.last().trim()
            if (last.isBlank()) {
                lines.removeAt(lines.lastIndex)
                continue
            }
            if (trailingTruncatedCite.matches(last)) {
                // 整行只是「[1] xxx」且很短 → 丢弃；保留句内正常的「…。[1]」。
                lines.removeAt(lines.lastIndex)
                continue
            }
            break
        }
        return lines.joinToString("\n").trim()
    }

    /** 过短、以标点开头、或有证据却几乎无重合 → 视为崩坏回答。 */
    fun isWeakGroundedAnswer(
        text: String,
        references: List<KnowledgeItem>,
    ): Boolean {
        val t = text.trim()
        if (t.isBlank()) return true
        val first = t.firstOrNull()
        if (first != null && first in ",，、;；?？.。") return true
        if (isMetaReasoningLead(t)) return true
        val top = references.firstOrNull()?.content.orEmpty()
        if (top.isBlank()) {
            // 无本地证据时：仅拦截极端短/空，避免误伤正常短答。
            return t.length < 2
        }
        if (t.length < 12) return true
        val overlap = tokenOverlapRatio(t, top)
        return overlap < 0.06f && t.length < 96
    }

    fun fallbackFromEvidence(references: List<KnowledgeItem>): String {
        val top = references.firstOrNull() ?: return "资料不足，无法回答。"
        val body =
            top.content
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(220)
                .trimEnd('，', ',', '。', '.')
        return if (body.isBlank()) {
            "资料不足，无法回答。"
        } else {
            "$body。[1]"
        }
    }

    fun coalesceGroundedAnswer(
        raw: String,
        references: List<KnowledgeItem>,
    ): String {
        val cleaned = stripMetaReasoningLead(raw)
        return if (isWeakGroundedAnswer(cleaned, references)) {
            fallbackFromEvidence(references)
        } else {
            cleaned
        }
    }

    private fun tokenOverlapRatio(
        answer: String,
        evidence: String,
    ): Float {
        val a = tokenize(answer)
        val e = tokenize(evidence)
        if (a.isEmpty() || e.isEmpty()) return 0f
        val hit = a.count { it in e }
        return hit.toFloat() / a.size.toFloat()
    }

    private fun tokenize(text: String): Set<String> {
        val out = linkedSetOf<String>()
        // CJK bigrams
        val han = text.filter { it.code in 0x4E00..0x9FFF }
        if (han.length >= 2) {
            for (i in 0 until han.length - 1) {
                out += han.substring(i, i + 2)
            }
        }
        Regex("[A-Za-z0-9]+|[\\u4E00-\\u9FFF]{2,}").findAll(text).forEach { out += it.value }
        return out
    }

    private fun normalize(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
