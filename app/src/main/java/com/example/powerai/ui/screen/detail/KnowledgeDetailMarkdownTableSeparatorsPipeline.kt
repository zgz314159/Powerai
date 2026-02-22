package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableSeparatorsPipeline {
    fun normalizeRowPipesAndInsertMissingSeparators(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        KnowledgeDetailMarkdownTableSeparatorInserter.normalizeRowPipesAndInsertMissingSeparators(
            lines = lines,
            separatorLineRegex = separatorLineRegex
        )
    }

    fun normalizeSeparatorWidthsAndPadBlankLines(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        KnowledgeDetailMarkdownTableSeparatorWidthNormalizer.normalizeSeparatorWidthsAndPadBlankLines(
            lines = lines,
            separatorLineRegex = separatorLineRegex
        )
    }
}
