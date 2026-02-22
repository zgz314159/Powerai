package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableBlockProtector {
    private const val PLACEHOLDER_PREFIX = "@@TABLE_BLOCK_"
    private const val PLACEHOLDER_SUFFIX = "@@"

    data class ProtectedBlocks(
        val protectedText: String,
        val blocks: List<String>
    )

    fun protect(markdown: String): ProtectedBlocks {
        val lines = markdown.split('\n')
        val tableBlocks = ArrayList<String>()
        val rebuilt = StringBuilder(markdown.length)

        var i = 0
        while (i < lines.size) {
            if (!KnowledgeDetailMarkdownTableNormalizeHelpers.isTableLine(lines[i])) {
                rebuilt.append(lines[i])
                if (i != lines.lastIndex) rebuilt.append('\n')
                i++
                continue
            }

            val start = i
            while (i < lines.size && KnowledgeDetailMarkdownTableNormalizeHelpers.isTableLine(lines[i])) {
                i++
            }

            val block = lines.subList(start, i).joinToString("\n")
            val idx = tableBlocks.size
            tableBlocks.add(block)

            rebuilt.append(PLACEHOLDER_PREFIX).append(idx).append(PLACEHOLDER_SUFFIX)
            if (i != lines.size) rebuilt.append('\n')
        }

        return ProtectedBlocks(
            protectedText = rebuilt.toString(),
            blocks = tableBlocks
        )
    }

    fun restore(text: String, blocks: List<String>): String {
        var restored = text
        for (idx in blocks.indices) {
            restored = restored.replace("$PLACEHOLDER_PREFIX${idx}$PLACEHOLDER_SUFFIX", blocks[idx])
        }
        return restored
    }
}
