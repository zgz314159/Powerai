package com.example.powerai.ui.screen.detail

internal fun ensureBlankLinesAroundImages(markdown: String): String {
    // Ensure markdown images are surrounded by blank lines so they won't be treated as table text.
    // IMPORTANT: do NOT do this inside markdown table blocks (lines starting with '|'), otherwise
    // we split a table row and Markwon will fail to recognize the table, which also makes images
    // "jump" out of their intended cells.

    // Protect table blocks by replacing them with placeholders, run the old logic on the rest,
    // then restore them. This keeps images embedded inside table cells intact.
    val protected = KnowledgeDetailMarkdownTableBlockProtector.protect(markdown)
    val protectedMarkdown = protected.protectedText

    val imageRegex = Regex("!\\[[^\\]]*]\\([^\\n)]+\\)")
    val matches = imageRegex.findAll(protectedMarkdown).toList()
    if (matches.isEmpty()) {
        // Nothing to do, just restore table blocks.
        return KnowledgeDetailMarkdownTableBlockProtector.restore(protectedMarkdown, protected.blocks)
    }

    val sb = StringBuilder(protectedMarkdown.length + matches.size * 4)
    var last = 0
    for (m in matches) {
        val start = m.range.first
        val end = m.range.last + 1
        sb.append(protectedMarkdown.substring(last, start))

        // ensure \n\n before
        if (sb.length >= 2 && sb.substring(sb.length - 2) == "\n\n") {
            // ok
        } else if (sb.isNotEmpty() && sb.last() == '\n') {
            sb.append('\n')
        } else if (sb.isNotEmpty()) {
            sb.append("\n\n")
        }

        sb.append(protectedMarkdown.substring(start, end))

        // ensure \n\n after
        val next2 = protectedMarkdown.substring(end, minOf(end + 2, protectedMarkdown.length))
        if (next2.startsWith("\n\n")) {
            // ok
        } else if (next2.startsWith("\n")) {
            sb.append('\n')
        } else {
            sb.append("\n\n")
        }

        last = end
    }
    sb.append(protectedMarkdown.substring(last))

    return KnowledgeDetailMarkdownTableBlockProtector.restore(sb.toString(), protected.blocks)
}
