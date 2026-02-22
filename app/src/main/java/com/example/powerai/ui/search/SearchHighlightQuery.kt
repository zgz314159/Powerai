package com.example.powerai.ui.search

import android.net.Uri

internal object SearchHighlightQuery {
    fun fromRawQuery(rawQuery: String): String = normalize(rawQuery)

    fun fromEncodedHighlight(rawHighlight: String): String {
        val decoded = Uri.decode(rawHighlight).trim()
        return normalize(decoded)
    }

    private fun normalize(raw: String): String {
        val normalized = SearchTextNormalization.normalizeForSearch(raw)

        if (normalized.isBlank()) return ""

        val hasCjk = SearchTextNormalization.containsCjkUnified(normalized)
        return SearchTextNormalization.dropLatinTailIfHasCjk(normalized, hasCjk)
    }
}
