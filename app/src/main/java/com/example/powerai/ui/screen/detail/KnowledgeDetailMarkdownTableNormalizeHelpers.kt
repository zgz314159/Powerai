package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableNormalizeHelpers {
    private val separatorLineRegex =
        Regex("^\\|\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|\\s*$")

    fun isTableLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("|") && t.count { it == '|' } >= 2
    }

    fun isSeparatorLine(line: String): Boolean {
        val t = line.trim()
        if (!t.startsWith("|")) return false
        // gfm separator: | --- | :---: | ---: |
        return separatorLineRegex.matches(t)
    }

    fun buildSeparatorRow(columns: Int): String {
        val c = columns.coerceAtLeast(1)
        return buildString {
            append('|')
            repeat(c) { append("---|") }
        }
    }

    fun splitCellsPreserveTrailingEmpty(line: String): List<String> {
        val raw = line.trim()
        if (raw.isEmpty()) return emptyList()
        if (!raw.startsWith("|")) return emptyList()
        var content = raw.removePrefix("|")
        if (content.endsWith("|")) {
            content = content.dropLast(1)
        }
        // Kotlin 的 split(limit) 不允许 -1；这里手动切分以保留尾随空字段。
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (idx in content.indices) {
            val ch = content[idx]
            val isEscapedPipe = ch == '|' && idx > 0 && content[idx - 1] == '\\'
            if (ch == '|' && !isEscapedPipe) {
                out.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    fun joinCells(cells: List<String>): String {
        return buildString {
            append('|')
            for (c in cells) {
                val cell = c.trim().replace(Regex("\\s{2,}"), " ")
                append(cell)
                append('|')
            }
        }
    }
}
