package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableDoublePipesSplitter {
    private val rowBoundaryBeforeSeparator = Regex("\\|\\|(?=\\s*:?-{3,}:?\\s*\\|)")
    private val rowBoundaryBeforeData = Regex("\\|\\|(?=\\s*[0-9A-Za-z\\u4E00-\\u9FFF])")

    fun splitDoublePipesToNewlines(text: String): String {
        return text
            .replace(rowBoundaryBeforeSeparator, "|\n|")
            .replace(rowBoundaryBeforeData, "|\n|")
    }
}
