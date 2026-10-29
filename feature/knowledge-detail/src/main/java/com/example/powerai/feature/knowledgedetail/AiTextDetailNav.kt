package com.example.powerai.feature.knowledgedetail

import android.net.Uri
import androidx.navigation.NavHostController

/**
 * Sparse / 无 Room id 的条文：用 savedStateHandle 传正文，再进 ai-detail 路由。
 */
object AiTextDetailNav {
    const val ROUTE = "ai-detail?q={q}"
    const val ARG_Q = "q"

    const val KEY_TITLE = "AI_DETAIL_TITLE"
    const val KEY_CONTENT = "AI_DETAIL_CONTENT"
    const val KEY_SOURCE = "AI_DETAIL_SOURCE"

    fun createRoute(highlight: String = ""): String {
        val q = Uri.encode(highlight)
        return "ai-detail?q=$q"
    }

    fun navigate(
        navController: NavHostController,
        title: String,
        content: String,
        highlight: String = "",
        source: String = "",
    ) {
        navController.currentBackStackEntry?.savedStateHandle?.set(KEY_TITLE, title)
        navController.currentBackStackEntry?.savedStateHandle?.set(KEY_CONTENT, content)
        navController.currentBackStackEntry?.savedStateHandle?.set(KEY_SOURCE, source)
        navController.navigate(createRoute(highlight))
    }
}
