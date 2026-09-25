package com.example.powerai.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.DrawerState

/**
 * AI搜索区域组件
 * 
 * 职责
 * - 展示AI聊天界面（ChatScaffold
 * - 将来自父级的输入和搜索事件转发到ChatScaffold
 * - 简化MainSearchAndResultsArea的代
 * 
 * 遵循SRP：只负责AI模式的UI呈现，不处理搜索逻辑
 */
@Composable
fun AiSearchArea(
    aiStreamViewModel: AiStreamViewModel,
    webSearchEnabled: Boolean,
    searchQuery: String,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onRetryAi: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    drawerState: DrawerState,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    val uiState by aiStreamViewModel.uiState.collectAsState()
    val isStreaming = uiState.isLoading
    val sessions = uiState.sessions
    val selectedSessionId = uiState.selectedSessionId
    val currentTurnId = uiState.currentTurnId

    // 处理搜索事件 - 执行搜索
    val handleSearch: (String) -> Unit = search@{ query ->
        val normalized = query.trim()
        if (normalized.isBlank()) return@search
        onQueryChange(normalized)
        aiStreamViewModel.onIntent(AiStreamIntent.AskAiStream(normalized, webSearchEnabled = webSearchEnabled))
    }

    // 处理清空事件
    val handleClear = {
        onClear()
    }

    AiChatScaffold(
        sessions = sessions,
        selectedSessionId = selectedSessionId,
        currentTurnId = currentTurnId,
        isStreaming = isStreaming,
        webSearchEnabled = webSearchEnabled,
        onWebSearchEnabledChange = onWebSearchEnabledChange,
        input = searchQuery,
        onInputChange = onQueryChange,
        onClear = handleClear,
        onSearch = handleSearch,
        onRetry = onRetryAi,
        onCopy = onCopyToClipboard,
        onSelectSession = { aiStreamViewModel.onIntent(AiStreamIntent.SelectSession(it)) },
        onNewSession = { aiStreamViewModel.onIntent(AiStreamIntent.NewSession) },
        onInputFocusChanged = { /* handled at parent level if needed */ },
        drawerState = drawerState,
        turnRetryAllowed = uiState.turnRetryAllowed,
        showSearchBar = showSearchBar,
        onShowSearchBarChange = onShowSearchBarChange,
        innerPadding = innerPadding
    )
}
