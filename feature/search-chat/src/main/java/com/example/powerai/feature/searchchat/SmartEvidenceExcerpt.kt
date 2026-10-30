package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem

/**
 * 规程相关事实的可验证原文选段：按句边界取完整句，禁止 `.take(N)` 半句截断。
 */
object SmartEvidenceExcerpt {
    private val sentenceSplit = Regex("[^。！？；\n]+[。！？；]?")

    fun splitSentences(content: String): List<String> =
        sentenceSplit
            .findAll(content)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()

    /**
     * 选取与问题主题最相关的完整句（可带邻句），保留否定/条件/职责主语。
     * 无句界时返回全文 trim（短条文常见），绝不按固定字符腰斩。
     */
    fun select(
        question: String,
        item: KnowledgeItem,
        maxSentences: Int = 3,
    ): String {
        val content = item.content.replace("\r\n", "\n").trim()
        if (content.isBlank()) return ""
        val sentences = splitSentences(content)
        if (sentences.isEmpty()) return content
        if (sentences.size == 1) return sentences.first()

        val terms = SmartEvidenceRanker.extractQueryTerms(question)
        val aspects = SmartEvidenceGrader.extractAspects(question)
        val scored =
            sentences.mapIndexed { index, sentence ->
                val hay = sentence
                var score = 0f
                if (terms.isNotEmpty()) {
                    score += terms.count { hay.contains(it, ignoreCase = true) }.toFloat() / terms.size
                }
                if (aspects.isNotEmpty()) {
                    score += aspects.count { hay.contains(it) }.toFloat() / aspects.size * 0.5f
                }
                // 规范语气保留
                if (Regex("必须|不得|不准|严禁|应由|一人|监护").containsMatchIn(hay)) score += 0.15f
                index to score
            }
        val bestIdx = scored.maxByOrNull { it.second }?.first ?: 0
        val window = linkedSetOf<Int>()
        window += bestIdx
        if (sentences[bestIdx].length < 48 && bestIdx + 1 < sentences.size) window += bestIdx + 1
        if (bestIdx > 0 && sentences[bestIdx].length < 32) window += bestIdx - 1
        return window
            .sorted()
            .take(maxSentences)
            .joinToString("") { sentences[it].let { s -> if (s.endsWith("。") || s.endsWith("；")) s else "$s。" } }
            .trim()
    }

    fun selectFromReferences(
        question: String,
        references: List<KnowledgeItem>,
    ): String {
        val top = references.firstOrNull() ?: return ""
        return select(question, top)
    }
}
