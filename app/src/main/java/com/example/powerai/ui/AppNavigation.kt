package com.example.powerai.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.powerai.navigation.Screen
import com.example.powerai.ui.jsonrepo.JsonRepositoryScreen
import com.example.powerai.ui.jsonrepo.JsonRepositoryViewModel
import com.example.powerai.ui.settings.SettingsScreen
import com.example.powerai.ui.settings.SettingsViewModel
import com.example.powerai.ui.screen.home.HomeScreen
import com.example.powerai.ui.screen.home.KnowledgeViewModel
import com.example.powerai.ui.screen.hybrid.HybridScreen
import com.example.powerai.ui.screen.hybrid.HybridViewModel

/**
 * Centralized app navigation host. Keeps navigation concerns out of Activity.
 */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            val vm = hiltViewModel<KnowledgeViewModel>()
            val settingsVm = hiltViewModel<SettingsViewModel>()
            HomeScreen(navController, vm, settingsVm)
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
    }
}
