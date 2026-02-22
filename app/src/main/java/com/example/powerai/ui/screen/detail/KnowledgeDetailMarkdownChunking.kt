package com.example.powerai.ui.screen.detail

internal fun chunkContent(content: String, maxChunkSize: Int = 1800): List<String> {
    if (content.isBlank()) return listOf("")

    // Prefer keeping line breaks stable; group lines into chunks, but never split inside a table.
    // If a table is larger than maxChunkSize, split by data rows while repeating header+separator.
    val lines = content.split('\n')
    val out = ArrayList<String>(maxOf(1, lines.size / 10))
    val sb = StringBuilder()

    fun flushTextChunk() {
        if (sb.isNotEmpty()) {
            out.add(sb.toString().trimEnd())
            sb.clear()
        }
    }

    fun addAsOwnChunk(block: String) {
        val trimmed = block.trimEnd()
        if (trimmed.isNotBlank()) out.add(trimmed)
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (!KnowledgeDetailMarkdownTableNormalizeHelpers.isTableLine(line)) {
            val addition = if (sb.isEmpty()) line else "\n$line"
            if (sb.length + addition.length > maxChunkSize) {
                flushTextChunk()
                sb.append(line)
            } else {
                sb.append(addition)
            }
            i++
            continue
        }

        // We hit a table block; flush any pending text chunk first.
        flushTextChunk()

        val start = i
        while (i < lines.size && KnowledgeDetailMarkdownTableNormalizeHelpers.isTableLine(lines[i])) {
            i++
        }
        val blockLines = lines.subList(start, i)
        val blockText = blockLines.joinToString("\n")

        if (blockText.length <= maxChunkSize) {
            addAsOwnChunk(blockText)
            continue
        }

        val split = KnowledgeDetailMarkdownChunkingTableSplit.splitOversizedTableOrNull(
            tableLines = blockLines,
            maxChunkSize = maxChunkSize
        )
        if (split != null) {
            for (part in split) addAsOwnChunk(part)
        } else {
            // Not a standard markdown table (missing separator) or already broken; keep as one chunk.
            addAsOwnChunk(blockText)
        }
    }

    flushTextChunk()
    return out.ifEmpty { listOf("") }
}
