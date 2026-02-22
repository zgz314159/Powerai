package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownChunkingTableSplit {
    fun splitOversizedTableOrNull(
        tableLines: List<String>,
        maxChunkSize: Int
    ): List<String>? {
        if (tableLines.size < 2) return null
        if (!KnowledgeDetailMarkdownTableNormalizeHelpers.isSeparatorLine(tableLines[1])) return null

        val header = tableLines[0]
        val sep = tableLines[1]
        val data = tableLines.subList(2, tableLines.size)

        val out = ArrayList<String>(maxOf(1, tableLines.size / 10))

        var current = StringBuilder()

        fun startTableChunk() {
            current = StringBuilder()
            current.append(header).append('\n').append(sep)
        }

        fun flushTableChunk() {
            val trimmed = current.toString().trimEnd()
            if (trimmed.isNotBlank()) out.add(trimmed)
        }

        startTableChunk()
        for (row in data) {
            val addition = "\n$row"
            if (current.length + addition.length > maxChunkSize) {
                // If even a single row is too large, emit it as plain text to avoid infinite loop.
                if (current.length <= (header.length + sep.length + 2)) {
                    val trimmedRow = row.trimEnd()
                    if (trimmedRow.isNotBlank()) out.add(trimmedRow)
                    startTableChunk()
                    continue
                }
                flushTableChunk()
                startTableChunk()
            }
            if (current.length + addition.length <= maxChunkSize) {
                current.append(addition)
            }
        }
        flushTableChunk()

        return out
    }
}
