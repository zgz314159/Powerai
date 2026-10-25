package com.example.powerai.ui.screen.detail

internal fun looksLikeMarkdownTableChunk(markdown: String): Boolean {
    val lines = markdown.replace("\r\n", "\n")
        .split('\n')
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
    if (lines.size < 2) return false
    val header = lines[0].trim()
    val sep = lines[1].trim()
    if (!header.startsWith("|") || header.count { it == '|' } < 2) return false
    return Regex("^\\|\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|\\s*$").matches(sep)
}

internal fun buildHtmlForMarkdownTable(tableMarkdown: String): String {
    val lines = tableMarkdown.replace("\r\n", "\n").split('\n')
    val tableLines = lines.filter { it.trim().startsWith("|") && it.count { ch -> ch == '|' } >= 2 }
    val rows = tableLines.map { MarkdownTableHtmlHelpers.splitCellsPreserveTrailingEmpty(it) }.filter { it.isNotEmpty() }
    val maxCols = rows.maxOfOrNull { it.size } ?: 1

    val headerCells = rows.getOrNull(0).orEmpty()
    val dataRows = rows.drop(2)

    val css = """
        body { margin: 0; padding: 0; font-family: sans-serif; font-size: 14px; line-height: 1.35; color: #111; -webkit-text-size-adjust: 100%; }
        .table-wrap { overflow-x: auto; -webkit-overflow-scrolling: touch; padding: 4px 0; }
        table { border-collapse: collapse; width: max-content; min-width: 100%; }
        th, td { border: 1px solid #BDBDBD; padding: 8px 10px; vertical-align: top; white-space: pre-wrap; word-break: break-word; }
        th { background: #F5F5F5; font-weight: 600; }
        td { background: #FFFFFF; }
        img { max-width: 100%; height: auto; display: block; margin: 6px auto; }
    """.trimIndent()

    val tableHtml = buildString {
        append("<div class=\"table-wrap\">")
        append("<table>")
        append("<thead>")
        append(MarkdownTableHtmlHelpers.renderRow(headerCells, "th", maxCols))
        append("</thead>")
        append("<tbody>")
        for (r in dataRows) {
            append(MarkdownTableHtmlHelpers.renderRow(r, "td", maxCols))
        }
        append("</tbody>")
        append("</table>")
        append("</div>")
    }

    return """
        <!doctype html>
        <html>
          <head>
            <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
            <style>$css</style>
          </head>
          <body>
            $tableHtml
          </body>
        </html>
    """.trimIndent()
}
