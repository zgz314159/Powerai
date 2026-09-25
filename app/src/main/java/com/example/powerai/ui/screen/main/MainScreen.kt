@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import com.example.powerai.ui.screen.main.MainSearchAndResultsArea

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.database.DatabaseScreen
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.ui.screen.mine.MineScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController, viewModel: HybridViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    // logging removed; diagnostics can be emitted from ViewModel if needed
    // `displayList` is computed inside MainSearchAndResultsArea where it's used
    val aiStreamViewModel: AiStreamViewModel = hiltViewModel()
    val dbViewModel: com.example.powerai.ui.screen.database.DatabaseViewModel = hiltViewModel()
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val dbUiState by dbViewModel.uiState.collectAsState()
    val sourceFileNames = dbUiState.sourceFileNames
    val dbCurrentQuery = dbUiState.currentQuery

    var selectedTab by rememberSaveable { mutableStateOf(MainBottomTab.SMART) }
    var aiInputFocused by rememberSaveable { mutableStateOf(false) }
    // Which tab currently requested the floating search bar (null = none)
    var searchBarTab by rememberSaveable { mutableStateOf<MainBottomTab?>(null) }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val aiDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerVisible =
        aiDrawerState.currentValue == DrawerValue.Open ||
            aiDrawerState.targetValue == DrawerValue.Open
    // Keep bottom bar out of the way whenever an in-page search bar is open.
    // Otherwise the search bar is rendered above bottom nav and appears "floating".
    val shouldHideBottomBar =
        (searchBarTab != null) ||
            (selectedTab == MainBottomTab.AI && (aiInputFocused || imeVisible)) ||
            isDrawerVisible

    // If user switches tabs, close any open floating search bar (avoid cross-tab leaks)
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        searchBarTab = null
    }

    // Database tab: if currently in a search state, back should first exit search results
    // and restore the full database list instead of exiting the app.
    BackHandler(
        enabled = selectedTab == MainBottomTab.DATABASE &&
            (dbCurrentQuery.isNotBlank() || searchQuery.isNotBlank())
    ) {
        searchQuery = ""
        searchBarTab = null
        dbViewModel.loadAll()
    }

    // Back press: when a floating search bar is open and the query is empty,
    // consume back to close the search bar instead of navigating away.
    BackHandler(enabled = (searchBarTab != null && searchQuery.isBlank())) {
        searchBarTab = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                onTabClick = { selectedTab = it },
                onTabLongClick = { tab ->
                    if (supportsLongPressSearch(tab)) {
                        searchBarTab = tab
                    }
                },
                shouldHide = shouldHideBottomBar
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Removed outer padding to allow content to bleed into system bars (Edge-to-Edge).
                // Tabs will handle innerPadding.bottom to avoid interactive elements being hidden.
        ) {
            // Main content area
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    MainBottomTab.DATABASE -> {
                        val showForMode = searchBarTab == selectedTab
                        DatabaseTabContent(
                            navController = navController,
                            dbViewModel = dbViewModel,
                            dbDrawerState = aiDrawerState,
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = dbSearch@{
                                val normalized = searchQuery.trim()
                                if (normalized.isBlank()) return@dbSearch
                                if (normalized != searchQuery) searchQuery = normalized
                                dbViewModel.search(normalized)
                                searchBarTab = null
                            },
                            onClear = {
                                searchQuery = ""
                                dbViewModel.loadAll()
                            },
                            showSearchBar = showForMode,
                            onShowSearchBarChange = { visible -> if (!visible) {
                                searchBarTab = null
                            } },
                            innerPadding = innerPadding
                        )
                    }
                    MainBottomTab.LOCAL, MainBottomTab.AI -> {
                        val mode = selectedTab.toDisplayMode()
                        // AI页长按底栏图标后显示 ChatInputBar（含“搜思考”切换）
                        val showForMode = searchBarTab == selectedTab
                        val searchAction = {
                            if (mode == DisplayMode.AI) {
                                if (!uiState.webSearchEnabled) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("当前为“思考”模式：未进行联网检索。切换到“搜索”以启用")
                                    }
                                }
                                aiStreamViewModel.onIntent(AiStreamIntent.AskAiStream(searchQuery, webSearchEnabled = uiState.webSearchEnabled))
                            } else {
                                viewModel.submitQuery(searchQuery, mode)
                            }
                        }

                        SearchTabContent(
                            navController = navController,
                            uiState = uiState,
                            searchQuery = searchQuery,
                            selectedMode = mode,
                            aiStreamViewModel = aiStreamViewModel,
                            onWebSearchEnabledChange = viewModel::setWebSearchEnabled,
                            onCopyToClipboard = { text ->
                                clipboard.setText(AnnotatedString(text))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("已复制到剪贴")
                                }
                            },
                            onSearch = searchAction,
                            onClear = {
                                searchQuery = ""
                                viewModel.clearCurrentResults()
                            },
                            onQueryChange = { query -> searchQuery = query },
                            onRetry = searchAction,
                            drawerState = aiDrawerState,
                            showSearchBar = showForMode,
                            onShowSearchBarChange = { visible -> if (!visible) {
                                searchBarTab = null
                            } },
                            sourceFileNameProvider = { item ->
                                resolveLocalSourceFileName(
                                    item = item,
                                    databaseFileName = sourceFileNames[item.id]
                                )
                            },
                            onPrefetchSourceFileNames = dbViewModel::prefetchSourceFileNames,
                            onOpenDatabaseSource = { item ->
                                searchQuery = ""
                                searchBarTab = null
                                selectedTab = MainBottomTab.DATABASE
                                dbViewModel.focusItem(item)
                            },
                            hybridViewModel = viewModel,
                            innerPadding = innerPadding
                        )
                    }
                    MainBottomTab.SMART -> {
                        val showForMode = searchBarTab == selectedTab
                        SmartDeepSeekTabContent(
                            navController = navController,
                            hybridViewModel = viewModel,
                            drawerState = aiDrawerState,
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClear = {
                                searchQuery = ""
                            },
                            onCopyToClipboard = { text ->
                                clipboard.setText(AnnotatedString(text))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("已复制到剪贴")
                                }
                            },
                            showSearchBar = showForMode,
                            onShowSearchBarChange = { visible -> if (!visible) {
                                searchBarTab = null
                            } },
                            innerPadding = innerPadding
                        )
                    }

                    MainBottomTab.QUIZ -> QuizTabContent(innerPadding = innerPadding)
                    MainBottomTab.MINE -> MineTabContent(navController = navController, innerPadding = innerPadding)
                }
            }

            if (!isDrawerVisible) {
                MainTopBar(
                    selectedTab = selectedTab,
                    onMenuClick = { aiDrawerState.open() },
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

private fun supportsLongPressSearch(tab: MainBottomTab): Boolean =
    tab == MainBottomTab.AI ||
            tab == MainBottomTab.LOCAL ||
            tab == MainBottomTab.DATABASE ||
            tab == MainBottomTab.SMART

private fun MainBottomTab.toDisplayMode(): DisplayMode = when (this) {
    MainBottomTab.LOCAL -> DisplayMode.LOCAL
    MainBottomTab.AI -> DisplayMode.AI
    MainBottomTab.SMART -> DisplayMode.SMART
    else -> DisplayMode.SMART
}

@Suppress("DEPRECATION")
enum class MainBottomTab(val label: String) {
    DATABASE("数据"),
    LOCAL("本地"),
    AI("AI"),
    SMART("智能"),
    QUIZ("答题"),
    MINE("我的");

    fun icon() = when (this) {
        DATABASE -> Icons.Default.Storage
        LOCAL -> Icons.Default.Folder
        AI -> Icons.Default.Chat
        SMART -> Icons.Default.AutoAwesome
        QUIZ -> Icons.Default.Quiz
        MINE -> Icons.Default.Person
    }
}
