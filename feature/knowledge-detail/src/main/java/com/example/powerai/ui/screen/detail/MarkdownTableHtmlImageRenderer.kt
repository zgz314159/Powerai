package com.example.powerai.ui.screen.detail

internal object MarkdownTableHtmlImageRenderer {
    private val imageRegex = Regex("!\\[[^\\]]*]\\(([^)\\n]+)\\)")

    fun markdownImagesToHtml(cellHtmlEscaped: String): String {
        return imageRegex.replace(cellHtmlEscaped) { m ->
            val url = m.groupValues.getOrNull(1).orEmpty().trim()
            if (url.isBlank()) "" else "<img src=\"${MarkdownTableHtmlEscaper.escapeHtmlPreservingBr(url)}\" />"
        }
    }
}
