package com.example.powerai.ui.screen.detail

internal fun fixTableSyntax(markdown: String): String {
    var out = markdown.replace("\r\n", "\n")

    // Defensive: ensure images are isolated so they won't be parsed as table text
    out = ensureBlankLinesAroundImages(out)

    // Fix pseudo-markdown tables coming from legacy conversions.
    // Always run block-wise normalization: a chunk may contain multiple tables where some are valid
    // but others are not; globally skipping normalization causes invalid ones to remain un-fixed.
    out = normalizeTableBlocks(out)

    // keep markdown compact but preserve blank lines needed for table parsing
    out = out.replace(Regex("\n{4,}"), "\n\n\n")
    return out
}

internal fun normalizeTableBlocks(markdown: String): String {
    val lines = markdown.split('\n')
    val out = ArrayList<String>(lines.size + 8)

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (!KnowledgeDetailMarkdownTableNormalizeHelpers.isTableLine(line)) {
            out.add(line)
            i++
            continue
        }

        // collect a contiguous table block
        val block = ArrayList<String>()
        while (i < lines.size && KnowledgeDetailMarkdownTableNormalizeHelpers.isTableLine(lines[i])) {
            block.add(lines[i])
            i++
        }

        if (block.isEmpty()) continue

        KnowledgeDetailMarkdownTableBlockWriter.ensureTwoBlankLinesBefore(out)
        out.addAll(KnowledgeDetailMarkdownTableBlockNormalizer.normalizeOrKeep(block))
        KnowledgeDetailMarkdownTableBlockWriter.appendTwoBlankLines(out)
    }

    return out.joinToString("\n")
}
