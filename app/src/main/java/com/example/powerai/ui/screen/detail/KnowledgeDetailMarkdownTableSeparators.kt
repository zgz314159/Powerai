package com.example.powerai.ui.screen.detail

// If a chunk looks like a pipe table but lacks a header separator row, insert one.
internal fun ensureTableSeparators(markdown: String): String {
    var normalized = markdown.replace("\r\n", "\n")

    // JSON tables might be concatenated into a single line: `|a|b||c|d||---|---||e|f|`
    // First, split likely row boundaries represented by `||`.
    normalized = KnowledgeDetailMarkdownTableEmbeddedSeparatorExpander.splitDoublePipesToNewlines(normalized)

    val separatorLineRegex = Regex("^\\s*\\|\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|\\s*$")

    // Expand lines that contain an embedded separator row into multiple lines.
    val lines = KnowledgeDetailMarkdownTableEmbeddedSeparatorExpander
        .expandLinesWithEmbeddedSeparatorRows(normalized, separatorLineRegex)
        .toMutableList()

    // Insert missing separator rows and normalize separator width.
    KnowledgeDetailMarkdownTableSeparatorsPipeline.normalizeRowPipesAndInsertMissingSeparators(
        lines = lines,
        separatorLineRegex = separatorLineRegex
    )

    // Ensure separator rows have enough columns and add blank lines around them.
    KnowledgeDetailMarkdownTableSeparatorsPipeline.normalizeSeparatorWidthsAndPadBlankLines(
        lines = lines,
        separatorLineRegex = separatorLineRegex
    )

    return lines.joinToString("\n")
}
