package com.example.powerai.ui.screen.hybrid

import com.example.powerai.ui.search.SearchTextNormalization

internal data class HybridLocalPagingQueryNormalization(
    val normalized: String,
    val queryNoSpace: String?,
    val hasLatin: Boolean,
    val hasCjk: Boolean
)

/**
 * Local paging search query policy used by Hybrid UI.
 *
 * Intentionally mirrors the previous inline logic in HybridViewModel to keep behavior stable.
 */
internal object HybridLocalPagingQuery {

    fun normalize(raw: String): HybridLocalPagingQueryNormalization {
        var normalized = SearchTextNormalization.normalizeForSearch(raw)

        val hasLatin = SearchTextNormalization.containsLatin(normalized)
        val hasCjk = SearchTextNormalization.containsCjkBasic(normalized)

        // If user typed CJK plus accidental latin tail (e.g. IME residue like "变压qi"), drop latin.
        normalized = SearchTextNormalization.dropLatinTailIfHasCjk(normalized, hasCjk)

        // Block too-short queries, except allow single CJK char.
        if (normalized.length < 2 && !(hasCjk && normalized.length == 1)) {
            return HybridLocalPagingQueryNormalization(
                normalized = normalized,
                queryNoSpace = null,
                hasLatin = hasLatin,
                hasCjk = hasCjk
            )
        }

        // Pinyin interception: if query contains Latin letters but no CJK, treat as pinyin and skip.
        if (hasLatin && !hasCjk) {
            return HybridLocalPagingQueryNormalization(
                normalized = normalized,
                queryNoSpace = null,
                hasLatin = hasLatin,
                hasCjk = hasCjk
            )
        }

        val qNoSpace = normalized.replace(Regex("\\s+"), "")
        if (qNoSpace.isBlank()) {
            return HybridLocalPagingQueryNormalization(
                normalized = normalized,
                queryNoSpace = null,
                hasLatin = hasLatin,
                hasCjk = hasCjk
            )
        }

        return HybridLocalPagingQueryNormalization(
            normalized = normalized,
            queryNoSpace = qNoSpace,
            hasLatin = hasLatin,
            hasCjk = hasCjk
        )
    }

    fun toPagingQueryNoSpaceOrNull(raw: String): String? = normalize(raw).queryNoSpace
}
