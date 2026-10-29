package com.example.powerai.ui.screen.detail

import androidx.navigation.NavHostController
import com.example.powerai.feature.knowledgedetail.AiTextDetailNav

internal data class AiTextDetailNavArgs(
    val title: String,
    val content: String,
    val highlightEncoded: String,
)

internal object AiTextDetailNavArgsReader {
    fun read(navController: NavHostController): AiTextDetailNavArgs {
        // Writer puts payload on the entry *before* navigate; reader looks at previous.
        val prev = navController.previousBackStackEntry
        val title =
            prev?.savedStateHandle?.get<String>(AiTextDetailNav.KEY_TITLE).orEmpty().ifBlank { "条文详情" }
        val content = prev?.savedStateHandle?.get<String>(AiTextDetailNav.KEY_CONTENT).orEmpty()
        val highlightEncoded =
            navController.currentBackStackEntry?.arguments?.getString(AiTextDetailNav.ARG_Q).orEmpty()
        return AiTextDetailNavArgs(
            title = title,
            content = content,
            highlightEncoded = highlightEncoded,
        )
    }
}
