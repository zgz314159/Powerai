package com.example.powerai.ui.screen.detail

internal object MarkdownTableRowRenderer {
    fun renderRow(cells: List<String>, cellTag: String, maxCols: Int): String {
        val cols = maxCols.coerceAtLeast(1)
        val padded = if (cells.size >= cols) cells.take(cols) else cells + List(cols - cells.size) { "" }
        return buildString {
            append("<tr>")
            for (c in padded) {
                val escaped = MarkdownTableHtmlEscaper.escapeHtmlPreservingBr(c)
                val withImgs = MarkdownTableHtmlImageRenderer.markdownImagesToHtml(escaped)
                append('<').append(cellTag).append('>')
                append(withImgs)
                append("</").append(cellTag).append('>')
            }
            append("</tr>")
        }
    }
}
