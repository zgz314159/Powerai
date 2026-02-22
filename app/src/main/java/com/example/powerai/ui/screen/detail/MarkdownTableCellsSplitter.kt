package com.example.powerai.ui.screen.detail

internal object MarkdownTableCellsSplitter {
    fun splitCellsPreserveTrailingEmpty(line: String): List<String> {
        val raw = line.trim()
        if (raw.isEmpty() || !raw.startsWith("|")) return emptyList()
        var content = raw.removePrefix("|")
        if (content.endsWith("|")) content = content.dropLast(1)
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
}
