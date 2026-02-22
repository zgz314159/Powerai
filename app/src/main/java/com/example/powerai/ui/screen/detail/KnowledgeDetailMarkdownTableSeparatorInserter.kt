package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableSeparatorInserter {
    fun normalizeRowPipesAndInsertMissingSeparators(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val t = line.trim()

            if (t.startsWith("|") && !separatorLineRegex.matches(t)) {
                lines[i] = KnowledgeDetailMarkdownTableSeparatorHelpers.normalizeRowPipes(t)
            }

            // Insert separator if a table row is followed by non-separator table row
            if (t.startsWith("|") && i + 1 < lines.size) {
                val nextT = lines[i + 1].trim()
                if (nextT.startsWith("|") && !separatorLineRegex.matches(nextT) && !separatorLineRegex.matches(t)) {
                    val cols =
                        (KnowledgeDetailMarkdownTableSeparatorHelpers.pipeCount(lines[i]) - 1).coerceAtLeast(1)
                    lines.add(i + 1, KnowledgeDetailMarkdownTableSeparatorHelpers.buildSeparatorRow(cols))
                }
            }

            i++
        }
    }
}
