package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.util.PLog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.database.DatabaseScreen
import com.example.powerai.ui.screen.database.DatabaseViewModel
import com.example.powerai.ui.screen.mine.MineScreen
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.ui.screen.hybrid.HybridUiState
import kotlinx.coroutines.launch

/**
 * Database tab content - displays DatabaseScreen with drawer for source files.
 */
@Composable
internal fun DatabaseTabContent(
    navController: NavHostController,
    dbViewModel: DatabaseViewModel,
    dbDrawerState: DrawerState,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    val logTag = "PowerAiDbDebug"
    val localCoroutineScope = rememberCoroutineScope()
    val dbUiState by dbViewModel.uiState.collectAsState()
    val currentDbQuery = dbUiState.currentQuery

    androidx.compose.runtime.LaunchedEffect(currentDbQuery) {
        if (currentDbQuery != searchQuery) {
            onQueryChange(currentDbQuery)
        }
    }

    ModalNavigationDrawer(
        drawerState = dbDrawerState,
        scrimColor = Color.Black.copy(alpha = 0.18f),
        drawerContent = {
            val dbUiState by dbViewModel.uiState.collectAsState()
            val directoryGroups by dbViewModel.directoryGroups.collectAsState()
            val importDiagnostics by dbViewModel.importDiagnostics.collectAsState()
            val importProgress by dbViewModel.importProgress.collectAsState()
            val drawerGroups = if (directoryGroups.isNotEmpty()) directoryGroups else dbUiState.groups
            val drawerLoading = dbUiState.isLoading && drawerGroups.isEmpty()
            androidx.compose.runtime.LaunchedEffect(
                dbUiState.isLoading,
                dbUiState.groups.size,
                directoryGroups.size,
                drawerGroups.size,
                currentDbQuery,
                importDiagnostics.importedCount,
                importDiagnostics.scannedCount
            ) {
                PLog.d(
                    logTag,
                    "DatabaseTabContent drawer loading=$drawerLoading uiGroups=${dbUiState.groups.size} directoryGroups=${directoryGroups.size} drawerGroups=${drawerGroups.size} query=${currentDbQuery.trim()} diagnostics=${importDiagnostics.importedCount}/${importDiagnostics.scannedCount} sample=${drawerGroups.take(3).joinToString { it.fileName }}"
                )
            }
            DatabaseHistoryDrawerContent(
                groups = drawerGroups,
                currentQuery = currentDbQuery,
                isLoading = drawerLoading,
                diagnostics = importDiagnostics,
                progress = importProgress,
                onSelectGroup = { groupKey ->
                    if (currentDbQuery.isNotBlank()) onQueryChange("")
                    dbViewModel.focusGroupFromDrawer(groupKey)
                    localCoroutineScope.launch { dbDrawerState.close() }
                },
                onRefreshDiagnostics = dbViewModel::refreshImportDiagnostics,
                onEdgeAction = {
                    localCoroutineScope.launch { dbDrawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DatabaseScreen(
                navController = navController,
                viewModel = dbViewModel,
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onClear = onClear,
                showTopSearchBar = false,
                isActive = true,
                innerPadding = innerPadding
            )

            if (showSearchBar) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SearchBar(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        onSearch = {
                            val normalized = searchQuery.trim()
                            if (normalized.isBlank()) return@SearchBar
                            if (normalized != searchQuery) onQueryChange(normalized)
                            onSearch()
                            onShowSearchBarChange(false)
                        },
                        onClear = onClear,
                        label = "搜索数据",
                        placeholder = "搜索数据",
                        autoFocus = showSearchBar
                    )
                }
            }
        }
    }
}

/**
 * Search tab content - displays search results for LOCAL, AI, or SMART modes.
 */
@Composable
internal fun SearchTabContent(
    navController: NavHostController,
    uiState: HybridUiState,
    searchQuery: String,
    selectedMode: DisplayMode,
    aiStreamViewModel: AiStreamViewModel,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    drawerState: DrawerState,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    onOpenDatabaseSource: (KnowledgeItem) -> Unit,
    sourceFileNameProvider: (KnowledgeItem) -> String,
    onPrefetchSourceFileNames: (List<KnowledgeItem>) -> Unit,
    hybridViewModel: HybridViewModel,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    MainSearchAndResultsArea(
        navController = navController,
        uiState = uiState,
        searchQuery = searchQuery,
        selectedMode = selectedMode,
        aiStreamViewModel = aiStreamViewModel,
        onWebSearchEnabledChange = onWebSearchEnabledChange,
        onCopyToClipboard = onCopyToClipboard,
        onSearch = onSearch,
        onClear = onClear,
        onQueryChange = onQueryChange,
        highlight = searchQuery,
        askedAtMillis = uiState.askedAtMillis,
        onRetry = onRetry,
        showEmptyState = uiState.question.isNotBlank(),
        isPageLoading = false,
        onRetryAi = onRetry,
        drawerState = drawerState,
        showSearchBar = showSearchBar,
        onShowSearchBarChange = onShowSearchBarChange,
        onOpenDatabaseSource = onOpenDatabaseSource,
        sourceFileNameProvider = sourceFileNameProvider,
        onPrefetchSourceFileNames = onPrefetchSourceFileNames,
        hybridViewModel = hybridViewModel,
        innerPadding = innerPadding
    )
}

/**
 * Quiz tab placeholder - stub implementation.
 */
@Composable
internal fun QuizTabContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "答题功能未实")
    }
}

/**
 * Mine tab content - displays user profile/settings.
 */
@Composable
internal fun MineTabContent(
    navController: NavHostController,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    MineScreen(navController = navController, showTopBar = false, innerPadding = innerPadding)
}
