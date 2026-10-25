package com.example.powerai.ui.screen.detail

// ============================================================================
// SECTION 1: DOUBLE PIPES SPLITTER (was DoublePipesSplitter.kt)
// ============================================================================

internal object TableDoublePipesSplitter {
    private val rowBoundaryBeforeSeparator = Regex("\\|\\|(?=\\s*:?-{3,}:?\\s*\\|)")
    private val rowBoundaryBeforeData = Regex("\\|\\|(?=\\s*[0-9A-Za-z\\u4E00-\\u9FFF])")

    fun splitDoublePipesToNewlines(text: String): String {
        return text
            .replace(rowBoundaryBeforeSeparator, "|\n|")
            .replace(rowBoundaryBeforeData, "|\n|")
    }
}

// ============================================================================
// SECTION 2: EMBEDDED SEPARATOR ROW SPLITTER (was EmbeddedSeparatorRowSplitter.kt)
// ============================================================================

internal object TableEmbeddedSeparatorRowSplitter {
    private val separatorStartRegex = Regex("\\|\\s*:?-{3,}")

    private fun isAllowedInSeparatorRow(ch: Char): Boolean {
        return ch == '|' || ch == '-' || ch == ':' || ch == ' ' || ch == '\t'
    }

    fun splitEmbeddedSeparatorOnce(line: String): List<String> {
        val m = separatorStartRegex.find(line) ?: return listOf(line)
        val start = m.range.first

        var i = start
        while (i < line.length) {
            val ch = line[i]
            if (!isAllowedInSeparatorRow(ch)) break

            if (ch == '|' && i + 2 < line.length) {
                val next = line[i + 1]
                val nextNext = line[i + 2]
                if (next == '|' && !isAllowedInSeparatorRow(nextNext)) {
                    val pre = line.substring(0, start).trimEnd()
                    val sep = line.substring(start, i + 1).trim()
                    val post = line.substring(i + 1).trimStart()
                    return buildList {
                        if (pre.isNotBlank()) add(pre)
                        if (sep.isNotBlank()) add(sep)
                        if (post.isNotBlank()) add(post)
                    }
                }
            }

            i++
        }

        val pre = line.substring(0, start).trimEnd()
        val sep = line.substring(start, i.coerceAtMost(line.length)).trim()
        val post = line.substring(i.coerceAtMost(line.length)).trimStart()
        return buildList {
            if (pre.isNotBlank()) add(pre)
            if (sep.isNotBlank()) add(sep)
            if (post.isNotBlank()) add(post)
        }
    }
}

// ============================================================================
// SECTION 3: EMBEDDED SEPARATOR ROW EXPANDER (was EmbeddedSeparatorRowExpander.kt)
// ============================================================================

internal object TableEmbeddedSeparatorRowExpander {
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
                    val split = TableEmbeddedSeparatorRowSplitter.splitEmbeddedSeparatorOnce(p)
                    if (split.size > 1) changed = true
                    for (s in split) {
                        val s2 = TableDoublePipesSplitter.splitDoublePipesToNewlines(s)
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

// ============================================================================
// SECTION 4: EMBEDDED SEPARATOR EXPANDER (was EmbeddedSeparatorExpander.kt)
// ============================================================================

internal object TableEmbeddedSeparatorExpander {
    fun splitDoublePipesToNewlines(text: String): String {
        return TableDoublePipesSplitter.splitDoublePipesToNewlines(text)
    }

    fun expandLinesWithEmbeddedSeparatorRows(
        normalized: String,
        separatorLineRegex: Regex
    ): List<String> {
        return TableEmbeddedSeparatorRowExpander.expandLinesWithEmbeddedSeparatorRows(
            normalized = normalized,
            separatorLineRegex = separatorLineRegex
        )
    }
}
