package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableEmbeddedSeparatorExpander {
    fun splitDoublePipesToNewlines(text: String): String {
        return KnowledgeDetailMarkdownTableDoublePipesSplitter.splitDoublePipesToNewlines(text)
    }

    fun expandLinesWithEmbeddedSeparatorRows(
        normalized: String,
        separatorLineRegex: Regex
    ): List<String> {
        return KnowledgeDetailMarkdownTableEmbeddedSeparatorRowExpander.expandLinesWithEmbeddedSeparatorRows(
            normalized = normalized,
            separatorLineRegex = separatorLineRegex
        )
    }
}
