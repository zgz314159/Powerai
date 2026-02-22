@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
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
    val TAG = "MainScreen"
    val aiStreamViewModel: AiStreamViewModel = hiltViewModel()
    val dbViewModel: com.example.powerai.ui.screen.database.DatabaseViewModel = hiltViewModel()
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedItemId by rememberSaveable { mutableStateOf<Long?>(null) }

    var selectedTab by rememberSaveable { mutableStateOf(MainBottomTab.SMART) }
    var aiInputFocused by rememberSaveable { mutableStateOf(false) }
    // Which tab currently requested the floating search bar (null = none)
    var searchBarTab by rememberSaveable { mutableStateOf<MainBottomTab?>(null) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val shouldHideBottomBar = selectedTab == MainBottomTab.AI && (aiInputFocused || imeVisible)

    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val aiDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // If user switches tabs, close any open floating search bar (avoid cross-tab leaks)
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        searchBarTab = null
    }

    // Back press: when a floating search bar is open and the query is empty,
    // consume back to close the search bar instead of navigating away.
    BackHandler(enabled = (searchBarTab != null && searchQuery.isBlank())) {
        searchBarTab = null
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (!shouldHideBottomBar) {
                NavigationBar {
                    MainBottomTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { /* handled by child to allow long-press */ },
                            icon = {
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { selectedTab = tab },
                                        onLongClick = {
                                            // open search bar for this tab only
                                            if (tab == MainBottomTab.AI || tab == MainBottomTab.LOCAL || tab == MainBottomTab.DATABASE || tab == MainBottomTab.SMART) {
                                                searchBarTab = tab
                                            }
                                        }
                                    )
                                ) {
                                    Icon(tab.icon(), contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main content area
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    MainBottomTab.DATABASE -> {
                        val localCoroutineScope = rememberCoroutineScope()
                        ModalNavigationDrawer(
                            drawerState = aiDrawerState,
                            drawerContent = {
                                val dbHistory by dbViewModel.searchHistory.collectAsState()
                                DatabaseHistoryDrawerContent(
                                    history = dbHistory,
                                    onSelectQuery = { q ->
                                        // fill query and search
                                        searchQuery = q
                                        dbViewModel.search(q)
                                        localCoroutineScope.launch { aiDrawerState.close() }
                                    },
                                    onClearHistory = {
                                        dbViewModel.clearSearchHistory()
                                        localCoroutineScope.launch { aiDrawerState.close() }
                                    }
                                )
                            }
                        ) {
                            // Overlay floating search bar when requested
                            Box(modifier = Modifier.fillMaxSize()) {
                                DatabaseScreen(
                                    navController = navController,
                                    viewModel = dbViewModel,
                                    searchQuery = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = {
                                        dbViewModel.search(searchQuery)
                                        searchBarTab = null
                                    },
                                    onClear = {
                                        searchQuery = ""
                                        dbViewModel.loadAll()
                                    },
                                    showTopSearchBar = false,
                                    isActive = (selectedTab == MainBottomTab.DATABASE)
                                )

                                if (searchBarTab == MainBottomTab.DATABASE) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .imePadding()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        shape = MaterialTheme.shapes.large,
                                        tonalElevation = 2.dp,
                                        color = Color.White
                                    ) {
                                        SearchBar(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            onSearch = {
                                                dbViewModel.search(searchQuery)
                                                searchBarTab = null
                                            },
                                            onClear = {
                                                searchQuery = ""
                                                dbViewModel.loadAll()
                                            },
                                            label = "",
                                            placeholder = "搜索数据库"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    MainBottomTab.LOCAL, MainBottomTab.AI, MainBottomTab.SMART -> {
                        val mode = when (selectedTab) {
                            MainBottomTab.LOCAL -> DisplayMode.LOCAL
                            MainBottomTab.AI -> DisplayMode.AI
                            else -> DisplayMode.SMART
                        }
                        val showForMode = searchBarTab == selectedTab
                        MainSearchAndResultsArea(
                            navController = navController,
                            uiState = uiState,
                            searchQuery = searchQuery,
                            expandedItemId = expandedItemId,
                            selectedMode = mode,
                            aiStreamViewModel = aiStreamViewModel,
                            onWebSearchEnabledChange = viewModel::setWebSearchEnabled,
                            aiInputFocusChanged = { focused -> aiInputFocused = focused },
                            onToggleExpand = { id -> expandedItemId = if (expandedItemId == id) null else id },
                            onCopyToClipboard = { text ->
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            onSearch = {
                                if (mode == DisplayMode.AI) {
                                    Log.d(TAG, "onSearch invoked ui.webSearchEnabled=${uiState.webSearchEnabled} query='${searchQuery.take(60)}'")
                                    if (!uiState.webSearchEnabled) {
                                        Toast.makeText(context, "当前为“思考”模式：未进行联网检索。切换到“搜索”以启用。", Toast.LENGTH_SHORT).show()
                                    }
                                    aiStreamViewModel.askAiStream(searchQuery, webSearchEnabled = uiState.webSearchEnabled)
                                } else {
                                    viewModel.submitQuery(searchQuery, mode)
                                }
                            },
                            onClear = { searchQuery = "" },
                            onQueryChange = { searchQuery = it },
                            onRetryAi = {
                                if (mode == DisplayMode.AI) {
                                    Log.d(TAG, "onRetryAi invoked ui.webSearchEnabled=${uiState.webSearchEnabled} query='${searchQuery.take(60)}'")
                                    if (!uiState.webSearchEnabled) {
                                        Toast.makeText(context, "当前为“思考”模式：未进行联网检索。切换到“搜索”以启用。", Toast.LENGTH_SHORT).show()
                                    }
                                    aiStreamViewModel.askAiStream(searchQuery, webSearchEnabled = uiState.webSearchEnabled)
                                } else {
                                    viewModel.submitQuery(searchQuery, mode)
                                }
                            },
                            drawerState = aiDrawerState,
                            showSearchBar = showForMode,
                            onShowSearchBarChange = { visible -> if (!visible) searchBarTab = null },
                            hybridViewModel = viewModel
                        )
                        // If SMART mode, ensure drawer opens SmartHistory
                        if (mode == DisplayMode.SMART) {
                            // drawer content is provided inside MainSearchAndResultsArea when SMART
                        }
                    }
                    MainBottomTab.QUIZ -> QuizPlaceholderScreen()
                    MainBottomTab.MINE -> MineScreen(navController = navController)
                }
            }

            // Layer: Gradient Mask and Top App Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White,
                                0.5f to Color.White,
                                0.75f to Color.White.copy(alpha = 0.9f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )

            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { coroutineScope.launch { aiDrawerState.open() } }, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "对话历史")
                    }
                },
                title = {
                    Text(
                        text = when (selectedTab) {
                            MainBottomTab.LOCAL -> "本地"
                            MainBottomTab.AI -> "AI"
                            MainBottomTab.SMART -> "智能"
                            else -> ""
                        }
                    )
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopCenter),
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainSearchAndResultsArea(
    navController: NavHostController,
    uiState: HybridViewModel.UiStateWithImport,
    searchQuery: String,
    expandedItemId: Long?,
    selectedMode: DisplayMode,
    aiStreamViewModel: AiStreamViewModel,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    aiInputFocusChanged: (Boolean) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRetryAi: () -> Unit,
    drawerState: DrawerState,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    hybridViewModel: HybridViewModel
) {
    val metaProvider = remember {
        { item: com.example.powerai.domain.model.KnowledgeItem ->
            buildString {
                append(item.source)
                item.pageNumber?.let { append(" · 第${it}页") }
                item.hitBlockIndex?.let { append(" · 命中块$it") }
            }
        }
    }

    // drawerState is provided by MainScreen; no local drawer state here

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedMode) {
            DisplayMode.LOCAL -> {
                val localCoroutineScope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        val localHistory by hybridViewModel.localSearchHistory.collectAsState()
                        LocalHistoryDrawerContent(
                            history = localHistory,
                            onSelectQuery = { q ->
                                // fill query and search
                                onQueryChange(q)
                                onSearch()
                                localCoroutineScope.launch { drawerState.close() }
                            },
                            onClearHistory = {
                                hybridViewModel.clearLocalSearchHistory()
                                localCoroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val shouldAnimate = uiState.references.size <= 40
                        LocalResultsPage(
                            localResults = uiState.references,
                            expandedItemId = expandedItemId,
                            onToggleExpand = onToggleExpand,
                            highlight = searchQuery,
                            metaProvider = metaProvider,
                            onOpenDetail = { id, blockIndex, blockId ->
                                val encoded = android.net.Uri.encode(searchQuery)
                                navController.navigate(com.example.powerai.navigation.Screen.Detail.createRoute(id, encoded, blockIndex, blockId))
                            },
                            currentPage = 1,
                            totalPages = 1,
                            hasPrev = false,
                            hasNext = false,
                            onPrev = {},
                            onNext = {},
                            showEmptyState = uiState.question.isNotBlank(),
                            topContent = if (uiState.answer.isNotBlank()) {
                                {
                                    ResponseBody(
                                        text = uiState.answer,
                                        isLoading = uiState.isLoading,
                                        onCopy = { onCopyToClipboard(uiState.answer) },
                                        onRetry = { /* no-op for local */ },
                                        allowRetry = false,
                                        onCitationClick = null,
                                        renderMarkdownWhenPossible = true
                                    )
                                }
                            } else {
                                null
                            },
                            animateItems = shouldAnimate,
                            isPageLoading = false
                        )

                        if (showSearchBar) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 2.dp,
                                color = Color.White
                            ) {
                                SearchBar(
                                    value = searchQuery,
                                    onValueChange = onQueryChange,
                                    onSearch = {
                                        onSearch()
                                        onShowSearchBarChange(false)
                                        // ensure main-level state cleared
                                        // handled by onShowSearchBarChange -> sets searchBarTab = null
                                    },
                                    onClear = onClear,
                                    label = "",
                                    placeholder = "搜索知识点"
                                )
                            }
                        }
                    }
                }
            }
            DisplayMode.AI -> {
                val isStreaming by aiStreamViewModel.isLoading.collectAsState()
                val sessions by aiStreamViewModel.sessions.collectAsState()
                val selectedSessionId by aiStreamViewModel.selectedSessionId.collectAsState()
                val currentTurnId by aiStreamViewModel.currentTurnId.collectAsState()

                AiChatScaffold(
                    sessions = sessions,
                    selectedSessionId = selectedSessionId,
                    currentTurnId = currentTurnId,
                    isStreaming = isStreaming,
                    webSearchEnabled = uiState.webSearchEnabled,
                    onWebSearchEnabledChange = onWebSearchEnabledChange,
                    input = searchQuery,
                    onInputChange = onQueryChange,
                    onClear = onClear,
                    onSearch = onSearch,
                    onRetry = onRetryAi,
                    onCopy = onCopyToClipboard,
                    onSelectSession = { aiStreamViewModel.selectSession(it) },
                    onNewSession = { aiStreamViewModel.newSession() },
                    onInputFocusChanged = aiInputFocusChanged,
                    drawerState = drawerState,
                    turnRetryAllowed = aiStreamViewModel.turnRetryAllowed.collectAsState().value,
                    showSearchBar = showSearchBar,
                    onShowSearchBarChange = onShowSearchBarChange
                )
            }
            DisplayMode.SMART -> {
                val localCoroutineScope = rememberCoroutineScope()
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        val smartHistory by hybridViewModel.smartSearchHistory.collectAsState()
                        SmartHistoryDrawerContent(
                            history = smartHistory,
                            onSelectQuery = { q ->
                                onQueryChange(q)
                                onSearch()
                                localCoroutineScope.launch { drawerState.close() }
                            },
                            onClearHistory = {
                                hybridViewModel.clearSmartSearchHistory()
                                localCoroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SearchBar(
                                value = searchQuery,
                                onValueChange = onQueryChange,
                                onSearch = onSearch,
                                onClear = onClear,
                                label = "搜索知识点",
                                suffix = null,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val shouldAnimate = uiState.references.size <= 40
                            SmartPage(
                                aiText = uiState.answer,
                                localResults = uiState.references,
                                expandedItemId = expandedItemId,
                                onToggleExpand = onToggleExpand,
                                highlight = searchQuery,
                                askedAtMillis = uiState.askedAtMillis,
                                metaProvider = metaProvider,
                                onOpenDetail = { id, blockIndex, blockId ->
                                    val encoded = android.net.Uri.encode(searchQuery)
                                    navController.navigate(com.example.powerai.navigation.Screen.Detail.createRoute(id, encoded, blockIndex, blockId))
                                },
                                onOpenAiDetail = { _, _ -> },
                                onRetry = onRetryAi,
                                onCopy = onCopyToClipboard,
                                currentPage = 1,
                                totalPages = 1,
                                hasPrev = false,
                                hasNext = false,
                                onPrev = {},
                                onNext = {},
                                showEmptyState = uiState.question.isNotBlank(),
                                animateItems = shouldAnimate,
                                isPageLoading = false
                            )
                        }

                        if (showSearchBar) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 2.dp,
                                color = Color.White
                            ) {
                                SearchBar(
                                    value = searchQuery,
                                    onValueChange = onQueryChange,
                                    onSearch = {
                                        onSearch()
                                        onShowSearchBarChange(false)
                                    },
                                    onClear = onClear,
                                    label = "",
                                    placeholder = "搜索知识点"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private enum class MainBottomTab(val label: String) {
    DATABASE("数据库"),
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuizPlaceholderScreen() {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("答题") }) }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(text = "答题功能未实现")
        }
    }
}
