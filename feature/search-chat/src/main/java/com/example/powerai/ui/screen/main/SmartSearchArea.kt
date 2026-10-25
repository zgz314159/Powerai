package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.ui.screen.hybrid.HybridUiState
import kotlinx.coroutines.launch

/**
 * SMART display mode section extracted from MainSearchAndResultsArea.
 */
@Composable
fun SmartSearchArea(
    uiState: HybridUiState,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    expandedItemId: Long?,
    onToggleExpand: (Long) -> Unit,
    highlight: String,
    askedAtMillis: Long? = null,
    metaProvider: ((com.example.powerai.core.model.KnowledgeItem) -> String?)? = null,
    onOpenDetail: (id: Long, blockIndex: Int?, blockId: String?, highlight: String?) -> Unit,
    onOpenAiDetail: (title: String, content: String) -> Unit,
    onRetry: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    currentPage: Int,
    totalPages: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showEmptyState: Boolean,
    animateItems: Boolean,
    isPageLoading: Boolean = false,
    pageSize: Int = 4,
    drawerState: androidx.compose.material3.DrawerState,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    hybridViewModel: HybridViewModel,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    val meta = metaProvider ?: { item ->
        buildString {
            append(item.source)
            item.pageNumber?.let { append(" · ${it}") }
            item.hitBlockIndex?.let { append(" · 命中it") }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val localCoroutineScope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                val uiState by hybridViewModel.uiState.collectAsState()
    val smartHistory = uiState.smartSearchHistory
                SmartHistoryDrawerContent(
                    history = smartHistory,
                    onSelectQuery = { q ->
                        val normalized = q.trim()
                        if (normalized.isBlank()) {
                            localCoroutineScope.launch { drawerState.close() }
                            return@SmartHistoryDrawerContent
                        }
                        onQueryChange(normalized)
                        onSearch()
                        onQueryChange("")
                        onShowSearchBarChange(false)
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
                SmartPage(
                    aiText = uiState.answer,
                    localResults = uiState.references,
                    highlight = highlight,
                    askedAtMillis = askedAtMillis,
                    metaProvider = meta,
                    onOpenDetail = { id, blockIndex, blockId, detailHighlight ->
                        onOpenDetail(id, blockIndex, blockId, detailHighlight)
                    },
                    onRetry = onRetry,
                    onCopy = onCopy,
                    showEmptyState = showEmptyState,
                    isPageLoading = isPageLoading
                )

                if (showSearchBar) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 2.dp,
                        color = Color.White
                    ) {
                        SearchBar(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            onSearch = {
                                val normalized = searchQuery.trim()
                                if (normalized.isBlank()) return@SearchBar
                                if (normalized != searchQuery) onQueryChange(normalized)
                                onSearch()
                                onQueryChange("")
                                onShowSearchBarChange(false)
                            },
                            onClear = onClear,
                            label = "搜索知识",
                            placeholder = "搜索知识",
                            autoFocus = showSearchBar
                        )
                    }
                }
            }
        }
    }
}
