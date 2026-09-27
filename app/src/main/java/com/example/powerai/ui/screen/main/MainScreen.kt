@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.ui.screen.hybrid.HybridViewModel

/**
 * App shell: scaffold, drawer, top/bottom bars and the wiring between
 * tab state ([MainScreenState]), back navigation ([MainScreenBackHandlers])
 * and feature content ([MainFeatureContent]).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun MainScreen(
    navController: NavHostController,
    viewModel: HybridViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val aiStreamViewModel: AiStreamViewModel = hiltViewModel()
    val dbViewModel: com.example.powerai.ui.screen.database.DatabaseViewModel = hiltViewModel()
    val dbUiState by dbViewModel.uiState.collectAsState()
    val dbCurrentQuery = dbUiState.currentQuery
    val state = rememberMainScreenState()

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    val coroutineScope = rememberCoroutineScope()
    val aiDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerVisible =
        aiDrawerState.currentValue == DrawerValue.Open ||
            aiDrawerState.targetValue == DrawerValue.Open
    // Keep bottom bar out of the way whenever an in-page search bar is open.
    // Otherwise the search bar is rendered above bottom nav and appears "floating".
    val shouldHideBottomBar =
        (state.searchBarTab != null) ||
            (state.selectedTab == MainBottomTab.AI && (state.aiInputFocused || imeVisible)) ||
            isDrawerVisible

    MainScreenBackHandlers(
        state = state,
        dbCurrentQuery = dbCurrentQuery,
        onExitDatabaseSearch = { dbViewModel.loadAll() },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            MainBottomBar(
                selectedTab = state.selectedTab,
                onTabClick = { state.selectedTab = it },
                onTabLongClick = { tab ->
                    if (supportsLongPressSearch(tab)) {
                        state.searchBarTab = tab
                    }
                },
                shouldHide = shouldHideBottomBar,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
            // Removed outer padding to allow content to bleed into system bars (Edge-to-Edge).
            // Tabs will handle innerPadding.bottom to avoid interactive elements being hidden.
        ) {
            // Main content area
            Box(modifier = Modifier.fillMaxSize()) {
                MainFeatureContent(
                    state = state,
                    navController = navController,
                    hybridViewModel = viewModel,
                    uiState = uiState,
                    aiStreamViewModel = aiStreamViewModel,
                    dbViewModel = dbViewModel,
                    drawerState = aiDrawerState,
                    snackbarHostState = snackbarHostState,
                    innerPadding = innerPadding,
                )
            }

            if (!isDrawerVisible) {
                MainTopBar(
                    selectedTab = state.selectedTab,
                    onMenuClick = { aiDrawerState.open() },
                    coroutineScope = coroutineScope,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
enum class MainBottomTab(val label: String) {
    DATABASE("数据"),
    LOCAL("本地"),
    AI("AI"),
    SMART("智能"),
    QUIZ("答题"),
    MINE("我的"),
    ;

    fun icon() =
        when (this) {
            DATABASE -> Icons.Default.Storage
            LOCAL -> Icons.Default.Folder
            AI -> Icons.Default.Chat
            SMART -> Icons.Default.AutoAwesome
            QUIZ -> Icons.Default.Quiz
            MINE -> Icons.Default.Person
        }
}
