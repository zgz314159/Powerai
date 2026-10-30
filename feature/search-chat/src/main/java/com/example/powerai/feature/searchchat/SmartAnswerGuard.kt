package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem

data class SmartFastPathAnswer(
    val answer: String,
    val reason: String,
)

object SmartAnswerGuard {
    const val INSUFFICIENT = "资料不足，无法回答。"

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
            "该用户的问题",
            "最后，我要确保",
            "推理过程",
            "我需要",
            "让我想想",
            "看起来像是",
        )

    private val reasoningLeadMarkers =
        listOf(
            "用户的问题",
            "该用户的问题",
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
            "参考了",
            "得出结论",
            "最终答案是",
            "因此，最终答案",
        )

    /** 单独出现即视为推理腔（不必凑满 2 个 marker）。 */
    private val strongMetaMarkers =
        listOf(
            "该用户的问题",
            "最终答案是",
            "因此，最终答案",
            "参考了",
            "得出结论",
        )

    /** 句末另起一行的残缺引用，如「[1] 10k」。 */
    private val truncatedCiteLine =
        Regex("""^\s*\[\d+\]\s*.{0,40}\s*$""")

    private val numberToken =
        Regex("""\d+(?:\.\d+)?\s*(?:万|千)?\s*(?:Ω|欧|MΩ|mΩ|兆欧|kΩ)?""")

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
        if (strongMetaMarkers.any { normalized.contains(it) }) return true
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
        return stripTruncatedCiteLines(body.replace(Regex("^\\s*[?？]+\\s*"), "").trim())
    }

    /** 去掉首尾残缺 [n] 标题行（模型常把无关标题截断再贴）。 */
    fun stripTruncatedCiteLines(text: String): String {
        if (text.isBlank()) return text
        val lines = text.replace("\r\n", "\n").split('\n').toMutableList()
        while (lines.isNotEmpty()) {
            val first = lines.first().trim()
            if (first.isBlank() || truncatedCiteLine.matches(first)) {
                lines.removeAt(0)
                continue
            }
            break
        }
        while (lines.isNotEmpty()) {
            val last = lines.last().trim()
            if (last.isBlank() || truncatedCiteLine.matches(last)) {
                lines.removeAt(lines.lastIndex)
                continue
            }
            break
        }
        return lines.joinToString("\n").trim()
    }

    /** @deprecated 使用 [stripTruncatedCiteLines] */
    fun stripTrailingTruncatedCites(text: String): String = stripTruncatedCiteLines(text)

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
            // 无证据时：任何非「资料不足」正文都视为不可用（禁止空上下文胡编留在界面）。
            return !t.contains("资料不足")
        }
        if (t.length < 12) return true
        val overlap = tokenOverlapRatio(t, top)
        return overlap < 0.06f && t.length < 96
    }

    /**
     * 答案里出现证据中不存在的电阻/数值 → 疑似胡编（变压器绝缘电阻 100000Ω 类）。
     */
    fun hasUngroundedNumericClaims(
        answer: String,
        references: List<KnowledgeItem>,
    ): Boolean {
        val nums = numberToken.findAll(answer).map { it.value.replace(Regex("\\s+"), "") }.toList()
        if (nums.isEmpty()) return false
        val hay =
            references.joinToString("\n") { "${it.title}\n${it.content}" }
                .replace(Regex("\\s+"), "")
        return nums.any { n ->
            val core = n.replace(Regex("""[Ω欧MmKk兆千]+"""), "")
            core.length >= 3 && !hay.contains(core)
        }
    }

    fun fallbackFromEvidence(
        references: List<KnowledgeItem>,
        question: String = "",
    ): String {
        val top = references.firstOrNull() ?: return INSUFFICIENT
        val body =
            SmartEvidenceExcerpt
                .select(question.ifBlank { top.title }, top)
                .replace(Regex("[ \\t]+"), " ")
                .trim()
        return if (body.isBlank()) {
            INSUFFICIENT
        } else {
            val cite = if (body.endsWith("。") || body.endsWith("；")) body else "$body。"
            "$cite[1]"
        }
    }

    fun coalesceGroundedAnswer(
        raw: String,
        references: List<KnowledgeItem>,
        question: String = "",
    ): String {
        val cleaned = stripMetaReasoningLead(raw)
        if (references.isEmpty()) {
            return if (cleaned.contains("资料不足")) cleaned.ifBlank { INSUFFICIENT } else INSUFFICIENT
        }
        // 推理腔 / 无据数值：拒答，禁止把无关证据整段贴回去（否则手套又回来）。
        if (isMetaReasoningLead(cleaned) || hasUngroundedNumericClaims(cleaned, references)) {
            return INSUFFICIENT
        }
        // 职责/顺序/否定结构改写 → 回退合格原文（有选段）或拒答
        if (SmartSemanticClaimGuard.hasRewriteViolation(cleaned, references)) {
            val excerpt = fallbackFromEvidence(references, question)
            return if (excerpt == INSUFFICIENT) INSUFFICIENT else excerpt
        }
        if (isWeakGroundedAnswer(cleaned, references)) {
            val top = references.first().content
            // 弱答且与证据几乎无重合 → 拒答；有一定重合才允许摘录回退。
            return if (tokenOverlapRatio(cleaned, top) < 0.08f && cleaned.length > 24) {
                INSUFFICIENT
            } else {
                fallbackFromEvidence(references, question)
            }
        }
        return cleaned
    }

    /**
     * 流式结束后是否用 [resolved] 覆盖界面上的 [currentVisible]。
     * 关键：短「资料不足」覆盖不了长胡编（旧逻辑要求 resolved 更长）。
     */
    fun shouldReplaceVisibleAnswer(
        resolved: String,
        currentVisible: String,
        references: List<KnowledgeItem>,
    ): Boolean {
        if (resolved.isBlank()) return false
        if (references.isEmpty()) return true
        if (resolved == INSUFFICIENT || resolved.contains("资料不足")) return true
        if (currentVisible.isBlank()) return true
        // 流式胡编（职责改写等）即使更长也必须被核验后的原文/拒答覆盖
        if (SmartSemanticClaimGuard.hasRewriteViolation(currentVisible, references)) return true
        if (resolved.length >= currentVisible.length) return true
        return isWeakGroundedAnswer(currentVisible, references) || isMetaReasoningLead(currentVisible)
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
