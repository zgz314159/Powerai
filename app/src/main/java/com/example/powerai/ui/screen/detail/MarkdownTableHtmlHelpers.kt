package com.example.powerai.ui.screen.detail

internal object MarkdownTableHtmlHelpers {
    fun splitCellsPreserveTrailingEmpty(line: String): List<String> {
        return MarkdownTableCellsSplitter.splitCellsPreserveTrailingEmpty(line)
    }

    fun escapeHtmlPreservingBr(input: String): String {
        return MarkdownTableHtmlEscaper.escapeHtmlPreservingBr(input)
    }

    fun markdownImagesToHtml(cellHtmlEscaped: String): String {
        return MarkdownTableHtmlImageRenderer.markdownImagesToHtml(cellHtmlEscaped)
    }

    fun renderRow(cells: List<String>, cellTag: String, maxCols: Int): String {
        return MarkdownTableRowRenderer.renderRow(cells, cellTag, maxCols)
    }
}
