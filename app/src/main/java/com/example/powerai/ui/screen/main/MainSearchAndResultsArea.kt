package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import java.net.URLEncoder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.ui.screen.hybrid.HybridUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainSearchAndResultsArea(
    navController: NavHostController,
    uiState: HybridUiState,
    searchQuery: String,
    expandedItemId: Long? = null,
    selectedMode: DisplayMode,
    aiStreamViewModel: AiStreamViewModel,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onToggleExpand: (Long) -> Unit = {},
    onCopyToClipboard: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onQueryChange: (String) -> Unit,
    highlight: String,
    askedAtMillis: Long?,
    onOpenAiDetail: (String, String) -> Unit = { _, _ -> },
    onRetry: () -> Unit,
    currentPage: Int = 1,
    totalPages: Int = 1,
    hasPrev: Boolean = false,
    hasNext: Boolean = false,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    showEmptyState: Boolean,
    animateItems: Boolean = true,
    isPageLoading: Boolean = false,
    onRetryAi: () -> Unit,
    drawerState: DrawerState,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    onOpenDatabaseSource: (KnowledgeItem) -> Unit = {},
    sourceFileNameProvider: (KnowledgeItem) -> String = { it.source },
    onPrefetchSourceFileNames: (List<KnowledgeItem>) -> Unit = {},
    hybridViewModel: HybridViewModel,
    innerPadding: PaddingValues = PaddingValues()
) {
    val metaProvider = remember {
        { item: KnowledgeItem ->
            buildString {
                append(sourceFileNameProvider(item))
                item.pageNumber?.let { append(" · ${it}") }
                item.hitBlockIndex?.let { append(" · 命中it") }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedMode) {
            DisplayMode.LOCAL -> {
                LocalSearchArea(
                    uiState = uiState,
                    searchQuery = searchQuery,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onClear = onClear,
                    expandedItemId = expandedItemId,
                    onToggleExpand = onToggleExpand,
                    highlight = highlight,
                    metaProvider = metaProvider,
                    onOpenDetail = { id, block, bid, hl ->
                        val finalHl = hl?.takeIf { it.isNotBlank() } ?: searchQuery
                        val encoded = URLEncoder.encode(finalHl, "UTF-8")
                        navController.navigate(com.example.powerai.navigation.Screen.Detail.createRoute(id, encoded, block, bid))
                    },
                    onRetry = onRetry,
                    onCopy = onCopyToClipboard,
                    currentPage = 1,
                    totalPages = 1,
                    hasPrev = false,
                    hasNext = false,
                    onPrev = {},
                    onNext = {},
                    showEmptyState = uiState.question.isNotBlank(),
                    animateItems = uiState.references.size <= 40,
                    isPageLoading = false,
                    pageSize = 10,
                    drawerState = drawerState,
                    showSearchBar = showSearchBar,
                    onShowSearchBarChange = onShowSearchBarChange,
                    hybridViewModel = hybridViewModel,
                    innerPadding = innerPadding
                )
            }
            DisplayMode.AI -> {
                AiSearchArea(
                    aiStreamViewModel = aiStreamViewModel,
                    webSearchEnabled = uiState.webSearchEnabled,
                    searchQuery = searchQuery,
                    onWebSearchEnabledChange = onWebSearchEnabledChange,
                    onQueryChange = onQueryChange,
                    onClear = onClear,
                    onSearch = onSearch,
                    onRetryAi = onRetryAi,
                    onCopyToClipboard = onCopyToClipboard,
                    drawerState = drawerState,
                    showSearchBar = showSearchBar,
                    onShowSearchBarChange = onShowSearchBarChange,
                    innerPadding = innerPadding
                )
            }
            DisplayMode.SMART -> {
                SmartSearchArea(
                    uiState = uiState,
                    searchQuery = searchQuery,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onClear = onClear,
                    expandedItemId = expandedItemId,
                    onToggleExpand = onToggleExpand,
                    highlight = highlight,
                    askedAtMillis = askedAtMillis,
                    metaProvider = metaProvider,
                    onOpenDetail = { id, block, bid, hl ->
                        val finalHl = hl?.takeIf { it.isNotBlank() } ?: searchQuery
                        val encoded = URLEncoder.encode(finalHl, "UTF-8")
                        navController.navigate(com.example.powerai.navigation.Screen.Detail.createRoute(id, encoded, block, bid))
                    },
                    onOpenAiDetail = onOpenAiDetail,
                    onRetry = onRetry,
                    onCopy = onCopyToClipboard,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onPrev = onPrev,
                    onNext = onNext,
                    showEmptyState = showEmptyState,
                    animateItems = animateItems,
                    isPageLoading = isPageLoading,
                    pageSize = 10,
                    drawerState = drawerState,
                    showSearchBar = showSearchBar,
                    onShowSearchBarChange = onShowSearchBarChange,
                    hybridViewModel = hybridViewModel,
                    innerPadding = innerPadding
                )
            }
        }
    }
}
