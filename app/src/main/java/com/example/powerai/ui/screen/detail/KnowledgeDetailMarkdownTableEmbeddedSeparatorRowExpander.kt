package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableEmbeddedSeparatorRowExpander {
    fun expandLinesWithEmbeddedSeparatorRows(
        normalized: String,
        separatorLineRegex: Regex
    ): List<String> {
        val expanded = ArrayList<String>()
        for (rawLine in normalized.split('\n')) {
            if (rawLine.isBlank()) {
                expanded.add(rawLine)
                continue
            }
            var parts = listOf(rawLine)
            var changed = true
            while (changed) {
                changed = false
                val newParts = ArrayList<String>()
                for (p in parts) {
                    if (separatorLineRegex.matches(p.trim())) {
                        newParts.add(p.trim())
                        continue
                    }
                    val split = KnowledgeDetailMarkdownTableEmbeddedSeparatorRowSplitter.splitEmbeddedSeparatorOnce(p)
                    if (split.size > 1) changed = true
                    for (s in split) {
                        val s2 = KnowledgeDetailMarkdownTableDoublePipesSplitter.splitDoublePipesToNewlines(s)
                        newParts.addAll(s2.split('\n'))
                    }
                }
                parts = newParts
            }
            expanded.addAll(parts)
        }
        return expanded
    }
}
