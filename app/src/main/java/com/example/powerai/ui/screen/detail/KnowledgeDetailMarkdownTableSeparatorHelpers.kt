package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableSeparatorHelpers {
    fun normalizeRowPipes(line: String): String {
        val t = line.trim()
        if (!t.startsWith("|")) return line
        return if (t.endsWith("|")) t else "$t|"
    }

    fun pipeCount(line: String): Int = line.count { it == '|' }

    fun buildSeparatorRow(columns: Int): String {
        val c = columns.coerceAtLeast(1)
        return buildString {
            append('|')
            repeat(c) { append("---|") }
        }
    }
}
