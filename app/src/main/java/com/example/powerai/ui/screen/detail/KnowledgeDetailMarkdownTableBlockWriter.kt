package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableBlockWriter {
    fun ensureTwoBlankLinesBefore(out: MutableList<String>) {
        // Force blank lines before table block
        if (out.isNotEmpty() && out.last().isNotBlank()) {
            out.add("")
            out.add("")
            return
        }

        if (out.isNotEmpty() && out.last().isBlank()) {
            // ensure at least 2 blank lines
            if (out.size >= 2) {
                if (out[out.size - 2].isNotBlank()) out.add("")
            } else {
                out.add("")
            }
        }
    }

    fun appendTwoBlankLines(out: MutableList<String>) {
        // Force blank lines after table block
        out.add("")
        out.add("")
    }
}
