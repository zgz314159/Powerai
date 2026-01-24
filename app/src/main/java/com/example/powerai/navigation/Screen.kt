package com.example.powerai.navigation

sealed class Screen(val route: String) {
    object Hybrid : Screen("hybrid")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object JsonRepo : Screen("jsonrepo")
}
