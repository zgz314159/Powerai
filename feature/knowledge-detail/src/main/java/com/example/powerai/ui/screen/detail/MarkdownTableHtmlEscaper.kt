package com.example.powerai.ui.screen.detail

internal object MarkdownTableHtmlEscaper {
    fun escapeHtmlPreservingBr(input: String): String {
        val token = "__BR__TOKEN__"
        val normalized = input
            .replace("<br/>", token, ignoreCase = true)
            .replace("<br>", token, ignoreCase = true)
            .replace("<br />", token, ignoreCase = true)
        val escaped = buildString(normalized.length + 16) {
            for (ch in normalized) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '\"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
        return escaped.replace(token, "<br/>")
    }
}
