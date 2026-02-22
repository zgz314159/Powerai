package com.example.powerai.ui.search

import com.example.powerai.data.importer.TextSanitizer

internal object SearchTextNormalization {
    fun normalizeForSearch(raw: String): String {
        return TextSanitizer.normalizeForSearch(raw)
            .replace('\n', ' ')
            .trim()
    }

    fun containsLatin(text: String): Boolean {
        return text.any { it in 'A'..'Z' || it in 'a'..'z' }
    }

    /**
     * Matches the historical behavior used by Hybrid local paging logic.
     */
    fun containsCjkBasic(text: String): Boolean {
        return text.any { it in '\u4e00'..'\u9fa5' }
    }

    /**
     * A broader CJK check used by highlight/navigation.
     */
    fun containsCjkUnified(text: String): Boolean {
        return text.any { it.code in 0x4E00..0x9FFF }
    }

    fun dropLatinTailIfHasCjk(normalized: String, hasCjk: Boolean): String {
        if (!hasCjk) return normalized
        return normalized
            .replace(Regex("[A-Za-z]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
