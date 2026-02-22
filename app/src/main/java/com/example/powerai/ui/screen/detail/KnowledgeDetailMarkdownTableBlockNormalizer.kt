package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableBlockNormalizer {
    fun normalizeOrKeep(block: List<String>): List<String> {
        if (block.isEmpty()) return emptyList()

        // Only attempt a real normalization when we have (or can identify) a separator row.
        // Otherwise, keep the block as-is to avoid turning arbitrary pipe text into a table.
        val hasSep = block.size >= 2 && KnowledgeDetailMarkdownTableNormalizeHelpers.isSeparatorLine(block[1])
        if (!hasSep) return block

        val parsed = block.map { KnowledgeDetailMarkdownTableNormalizeHelpers.splitCellsPreserveTrailingEmpty(it) }
        val maxCols = parsed.maxOfOrNull { it.size } ?: 0
        val cols = maxCols.coerceAtLeast(1)

        val out = ArrayList<String>(block.size + 2)

        // Rebuild header (pad to max cols)
        val headerCells = (parsed.getOrNull(0) ?: emptyList()).let { row ->
            if (row.size >= cols) row.take(cols) else row + List(cols - row.size) { "" }
        }
        out.add(KnowledgeDetailMarkdownTableNormalizeHelpers.joinCells(headerCells))

        // Rebuild separator (always cols)
        out.add(KnowledgeDetailMarkdownTableNormalizeHelpers.buildSeparatorRow(cols))

        // Rebuild data rows (pad to cols)
        for (r in 2 until block.size) {
            val row = parsed.getOrNull(r) ?: emptyList()
            val padded = if (row.size >= cols) row.take(cols) else row + List(cols - row.size) { "" }
            out.add(KnowledgeDetailMarkdownTableNormalizeHelpers.joinCells(padded))
        }

        return out
    }
}
