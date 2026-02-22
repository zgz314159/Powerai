package com.example.powerai.ui.screen.detail

import androidx.navigation.NavHostController

internal data class AiTextDetailNavArgs(
    val title: String,
    val content: String,
    val highlightEncoded: String
)

internal object AiTextDetailNavArgsReader {
    private const val KEY_TITLE = "AI_DETAIL_TITLE"
    private const val KEY_CONTENT = "AI_DETAIL_CONTENT"
    private const val ARG_HIGHLIGHT = "q"

    fun read(navController: NavHostController): AiTextDetailNavArgs {
        val prev = navController.previousBackStackEntry
        val title = prev?.savedStateHandle?.get<String>(KEY_TITLE).orEmpty().ifBlank { "AI 内容" }
        val content = prev?.savedStateHandle?.get<String>(KEY_CONTENT).orEmpty()
        val highlightEncoded = navController.currentBackStackEntry?.arguments?.getString(ARG_HIGHLIGHT).orEmpty()
        return AiTextDetailNavArgs(
            title = title,
            content = content,
            highlightEncoded = highlightEncoded
        )
    }
}
