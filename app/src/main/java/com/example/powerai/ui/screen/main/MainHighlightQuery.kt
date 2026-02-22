package com.example.powerai.ui.screen.main

import com.example.powerai.ui.search.SearchHighlightQuery

internal object MainHighlightQuery {

    /**
     * Mirrors repository-side normalization intent for highlight/navigation:
     * - normalize whitespace
     * - for CJK queries, drop accidental latin tail (IME residue)
     */
    fun effectiveHighlight(rawQuery: String): String {
        return SearchHighlightQuery.fromRawQuery(rawQuery)
    }
}
