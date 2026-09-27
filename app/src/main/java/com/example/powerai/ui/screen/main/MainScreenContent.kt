package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavHostController
import com.example.powerai.ui.screen.hybrid.HybridUiState
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import kotlinx.coroutines.launch

/**
 * Feature content dispatch for the main shell: routes the selected bottom
 * tab to its feature content without inlining feature business UI here.
 */
@Suppress("LongParameterList", "ktlint:standard:function-naming")
@Composable
internal fun MainFeatureContent(
    state: MainScreenState,
    navController: NavHostController,
    hybridViewModel: HybridViewModel,
    uiState: HybridUiState,
    aiStreamViewModel: AiStreamViewModel,
    dbViewModel: com.example.powerai.ui.screen.database.DatabaseViewModel,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    innerPadding: PaddingValues,
) {
    when (state.selectedTab) {
        MainBottomTab.DATABASE ->
            DatabaseTabDispatch(
                state = state,
                navController = navController,
                dbViewModel = dbViewModel,
                drawerState = drawerState,
                innerPadding = innerPadding,
            )

        MainBottomTab.LOCAL, MainBottomTab.AI ->
            SearchTabDispatch(
                state = state,
                navController = navController,
                hybridViewModel = hybridViewModel,
                uiState = uiState,
                aiStreamViewModel = aiStreamViewModel,
                dbViewModel = dbViewModel,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                innerPadding = innerPadding,
            )

        MainBottomTab.SMART ->
            SmartTabDispatch(
                state = state,
                navController = navController,
                hybridViewModel = hybridViewModel,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                innerPadding = innerPadding,
            )

        MainBottomTab.QUIZ -> QuizTabContent(innerPadding = innerPadding)
        MainBottomTab.MINE -> MineTabContent(navController = navController, innerPadding = innerPadding)
    }
}

@Suppress("LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun DatabaseTabDispatch(
    state: MainScreenState,
    navController: NavHostController,
    dbViewModel: com.example.powerai.ui.screen.database.DatabaseViewModel,
    drawerState: DrawerState,
    innerPadding: PaddingValues,
) {
    val showForMode = state.searchBarTab == state.selectedTab
    DatabaseTabContent(
        navController = navController,
        dbViewModel = dbViewModel,
        dbDrawerState = drawerState,
        searchQuery = state.searchQuery,
        onQueryChange = { state.searchQuery = it },
        onSearch = dbSearch@{
            val normalized = state.searchQuery.trim()
            if (normalized.isBlank()) return@dbSearch
            if (normalized != state.searchQuery) state.searchQuery = normalized
            dbViewModel.search(normalized)
            state.searchBarTab = null
        },
        onClear = {
            state.searchQuery = ""
            dbViewModel.loadAll()
        },
        showSearchBar = showForMode,
        onShowSearchBarChange = { visible ->
            if (!visible) {
                state.searchBarTab = null
            }
        },
        innerPadding = innerPadding,
    )
}

@Suppress("LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun SearchTabDispatch(
    state: MainScreenState,
    navController: NavHostController,
    hybridViewModel: HybridViewModel,
    uiState: HybridUiState,
    aiStreamViewModel: AiStreamViewModel,
    dbViewModel: com.example.powerai.ui.screen.database.DatabaseViewModel,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    innerPadding: PaddingValues,
) {
    val mode = state.selectedTab.toDisplayMode()
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val dbUiState by dbViewModel.uiState.collectAsState()
    val sourceFileNames = dbUiState.sourceFileNames
    // AI页长按底栏图标后显示 ChatInputBar（含“搜思考”切换）
    val showForMode = state.searchBarTab == state.selectedTab
    val searchAction = {
        if (mode == DisplayMode.AI) {
            if (!uiState.webSearchEnabled) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("当前为“思考”模式：未进行联网检索。切换到“搜索”以启用")
                }
            }
            aiStreamViewModel.onIntent(
                AiStreamIntent.AskAiStream(state.searchQuery, webSearchEnabled = uiState.webSearchEnabled),
            )
        } else {
            hybridViewModel.submitQuery(state.searchQuery, mode)
        }
    }

    SearchTabContent(
        navController = navController,
        uiState = uiState,
        searchQuery = state.searchQuery,
        selectedMode = mode,
        aiStreamViewModel = aiStreamViewModel,
        onWebSearchEnabledChange = hybridViewModel::setWebSearchEnabled,
        onCopyToClipboard = { text ->
            clipboard.setText(AnnotatedString(text))
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已复制到剪贴")
            }
        },
        onSearch = searchAction,
        onClear = {
            state.searchQuery = ""
            hybridViewModel.clearCurrentResults()
        },
        onQueryChange = { query -> state.searchQuery = query },
        onRetry = searchAction,
        drawerState = drawerState,
        showSearchBar = showForMode,
        onShowSearchBarChange = { visible ->
            if (!visible) {
                state.searchBarTab = null
            }
        },
        sourceFileNameProvider = { item ->
            resolveLocalSourceFileName(
                item = item,
                databaseFileName = sourceFileNames[item.id],
            )
        },
        onPrefetchSourceFileNames = dbViewModel::prefetchSourceFileNames,
        onOpenDatabaseSource = { item ->
            state.searchQuery = ""
            state.searchBarTab = null
            state.selectedTab = MainBottomTab.DATABASE
            dbViewModel.focusItem(item)
        },
        hybridViewModel = hybridViewModel,
        innerPadding = innerPadding,
    )
}

@Suppress("LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun SmartTabDispatch(
    state: MainScreenState,
    navController: NavHostController,
    hybridViewModel: HybridViewModel,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    innerPadding: PaddingValues,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val showForMode = state.searchBarTab == state.selectedTab
    SmartDeepSeekTabContent(
        navController = navController,
        hybridViewModel = hybridViewModel,
        drawerState = drawerState,
        searchQuery = state.searchQuery,
        onQueryChange = { state.searchQuery = it },
        onClear = {
            state.searchQuery = ""
        },
        onCopyToClipboard = { text ->
            clipboard.setText(AnnotatedString(text))
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已复制到剪贴")
            }
        },
        showSearchBar = showForMode,
        onShowSearchBarChange = { visible ->
            if (!visible) {
                state.searchBarTab = null
            }
        },
        innerPadding = innerPadding,
    )
}

private fun MainBottomTab.toDisplayMode(): DisplayMode =
    when (this) {
        MainBottomTab.LOCAL -> DisplayMode.LOCAL
        MainBottomTab.AI -> DisplayMode.AI
        MainBottomTab.SMART -> DisplayMode.SMART
        else -> DisplayMode.SMART
    }
