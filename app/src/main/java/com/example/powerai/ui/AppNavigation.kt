package com.example.powerai.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.powerai.navigation.Screen
import com.example.powerai.ui.jsonrepo.JsonRepositoryScreen
import com.example.powerai.ui.jsonrepo.JsonRepositoryViewModel
import com.example.powerai.ui.screen.detail.KnowledgeDetailScreen
import com.example.powerai.ui.screen.hybrid.HybridScreen
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.ui.screen.main.MainScreen
import com.example.powerai.ui.screen.pdf.PdfViewerScreen
import com.example.powerai.ui.screen.settings.SettingsScreen
import com.example.powerai.ui.screen.settings.SettingsViewModel

/**
 * Centralized app navigation host. Keeps navigation concerns out of Activity.
 */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            val vm = hiltViewModel<HybridViewModel>()
            MainScreen(navController, vm)
        }
        composable(Screen.Hybrid.route) {
            val vm = hiltViewModel<HybridViewModel>()
            HybridScreen(vm)
        }
        composable(Screen.Settings.route) {
            val vm = hiltViewModel<SettingsViewModel>()
            SettingsScreen(navController, vm)
        }
        composable(Screen.JsonRepo.route) {
            val vm = hiltViewModel<JsonRepositoryViewModel>()
            JsonRepositoryScreen(navController, vm)
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument(Screen.Detail.ARG_ID) { type = NavType.LongType },
                navArgument(Screen.Detail.ARG_Q) { type = NavType.StringType; defaultValue = "" },
                navArgument(Screen.Detail.ARG_BLOCK_INDEX) { type = NavType.IntType; defaultValue = -1 },
                navArgument(Screen.Detail.ARG_BLOCK_ID) { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            KnowledgeDetailScreen(navController)
        }

        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument(Screen.PdfViewer.ARG_FILE_ID) { type = NavType.StringType },
                navArgument(Screen.PdfViewer.ARG_NAME) { type = NavType.StringType },
                navArgument(Screen.PdfViewer.ARG_PAGE) { type = NavType.IntType; defaultValue = -1 },
                navArgument(Screen.PdfViewer.ARG_BBOX) { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStack ->
            val fileId = backStack.arguments?.getString(Screen.PdfViewer.ARG_FILE_ID).orEmpty()
            val nameEnc = backStack.arguments?.getString(Screen.PdfViewer.ARG_NAME).orEmpty()
            val fileName = Uri.decode(nameEnc)
            val page = backStack.arguments?.getInt(Screen.PdfViewer.ARG_PAGE)?.takeIf { it > 0 }
            val bboxEnc = backStack.arguments?.getString(Screen.PdfViewer.ARG_BBOX).orEmpty().takeIf { it.isNotBlank() }
            val bboxJson = bboxEnc?.let { Uri.decode(it) }
            PdfViewerScreen(
                navController = navController,
                fileId = fileId,
                fileName = fileName,
                initialHighlightPage = page,
                initialHighlightBboxJson = bboxJson
            )
        }
    }
}
