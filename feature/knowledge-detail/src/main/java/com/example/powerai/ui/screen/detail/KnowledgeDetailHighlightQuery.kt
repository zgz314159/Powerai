package com.example.powerai.ui.screen.detail

import com.example.powerai.ui.search.SearchHighlightQuery

internal object KnowledgeDetailHighlightQuery {
    fun effectiveHighlight(rawHighlight: String): String {
        return SearchHighlightQuery.fromEncodedHighlight(rawHighlight)
    }
}
