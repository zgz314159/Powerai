package com.example.powerai.data.repository

import com.example.powerai.data.importer.TextSanitizer

internal data class KnowledgeLocalSearchQuery(
    val raw: String,
    val normalized: String,
    val noSpace: String,
    val hasCjk: Boolean
)

internal object KnowledgeLocalSearchQueryBuilder {
    fun buildOrNull(query: String): KnowledgeLocalSearchQuery? {
        val raw = query.trim()
        if (raw.isBlank()) return null

        var normalized = TextSanitizer.normalizeForSearch(raw).replace('\n', ' ').trim()
        if (normalized.isBlank()) return null

        val hasCjk = normalized.any { it.code in 0x4E00..0x9FFF }

        // Avoid doing heavy work while the user is still typing pinyin/short queries.
        // Allow single CJK char queries like "电".
        if (normalized.length < 2 && !(hasCjk && normalized.length == 1)) return null

        // If query contains CJK, drop IME latin residue (e.g. "变压qi" -> "变压").
        if (hasCjk) {
            normalized = normalized
                .replace(Regex("[A-Za-z]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (normalized.isBlank()) return null
        }

        val noSpace = normalized.replace(Regex("\\s+"), "")
        if (noSpace.isBlank()) return null

        return KnowledgeLocalSearchQuery(
            raw = raw,
            normalized = normalized,
            noSpace = noSpace,
            hasCjk = hasCjk
        )
    }
}
