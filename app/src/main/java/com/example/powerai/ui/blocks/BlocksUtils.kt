package com.example.powerai.ui.blocks

import com.example.powerai.data.importer.TextSanitizer

/**
 * Helpers for matching/search-highlighting/navigation.
 */
object BlocksUtils {

    fun plainText(block: KnowledgeBlock): String {
        return when (block) {
            is TextBlock -> block.text
            is ImageBlock -> listOfNotNull(block.alt, block.caption).joinToString(" ")
            is ListBlock -> block.items.joinToString("\n")
            is TableBlock -> block.rows.joinToString("\n") { row -> row.joinToString("\t") }
            is CodeBlock -> block.code
            is UnknownBlock -> block.rawText
        }
    }

    /**
     * Normalized text used for matching. This uses the same rule as DB indexing (`contentNormalized`).
     */
    fun normalizedPlainText(block: KnowledgeBlock): String {
        return TextSanitizer.normalizeForSearch(plainText(block)).lowercase()
    }

    /**
     * Match against pre-normalized block texts.
     */
    fun matchIndicesNormalized(normalizedBlockTexts: List<String>, keyword: String): List<Int> {
        val k = keyword.trim()
        if (k.isBlank()) return emptyList()

        val needle = TextSanitizer.normalizeForSearch(k).lowercase()
        if (needle.isBlank()) return emptyList()
        val needleNoSpace = needle.replace(Regex("\\s+"), "")

        val out = ArrayList<Int>(8)
        for ((idx, hay) in normalizedBlockTexts.withIndex()) {
            if (hay.contains(needle)) {
                out.add(idx)
                continue
            }
            if (needleNoSpace.isNotBlank()) {
                val hayNoSpace = hay.replace(Regex("\\s+"), "")
                if (hayNoSpace.contains(needleNoSpace)) out.add(idx)
            }
        }
        return out
    }

    fun firstMatchIndex(blocks: List<KnowledgeBlock>, keyword: String): Int? {
        return matchIndices(blocks, keyword).firstOrNull()
    }

    /**
     * Return all matching block indices (0-based).
     *
     * Matching rules:
     * - use the same normalization as `contentNormalized` indexing (letters/digits only)
     * - case-insensitive
     * - also attempts whitespace-insensitive contains to improve recall
     */
    fun matchIndices(blocks: List<KnowledgeBlock>, keyword: String): List<Int> {
        val normalized = blocks.map { normalizedPlainText(it) }
        return matchIndicesNormalized(normalized, keyword)
    }
}
