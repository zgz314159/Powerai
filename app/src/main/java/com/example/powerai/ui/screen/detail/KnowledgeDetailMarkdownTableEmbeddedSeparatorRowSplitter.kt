package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableEmbeddedSeparatorRowSplitter {
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
