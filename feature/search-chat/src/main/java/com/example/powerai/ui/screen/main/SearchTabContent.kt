package com.example.powerai.ui.screen.main

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.ui.screen.hybrid.HybridUiState
import com.example.powerai.ui.screen.hybrid.HybridViewModel

/**
 * Search tab content - displays search results for LOCAL, AI, or SMART modes.
 */
@Composable
fun SearchTabContent(
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
    innerPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
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
        innerPadding = innerPadding,
    )
}
