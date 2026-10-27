package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.example.powerai.domain.model.chat.ChatSession

/**
 * Core chat area (list + input). Separated from the scaffold so the latter stays
 * focused on navigation/drawer concerns.
 */
@Composable
internal fun ChatContent(
    sessions: List<ChatSession>,
    selectedSessionId: Long?,
    currentTurnId: Long?,
    isStreaming: Boolean,
    webSearchEnabled: Boolean,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    turnRetryAllowed: Map<Long, Boolean>,
    onInputFocusChanged: (Boolean) -> Unit,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
) {
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        ChatList(
            turns =
                sessions.firstOrNull { it.id == selectedSessionId }?.turns
                    ?: sessions.firstOrNull()?.turns
                    ?: emptyList(),
            currentTurnId = currentTurnId,
            isStreaming = isStreaming,
            turnRetryAllowed = turnRetryAllowed,
            inputActive = (input.isNotBlank()),
            onInputChange = onInputChange,
            onCopy = onCopy,
            onRetry = onRetry,
        )

        ChatInputBar(
            modifier = androidx.compose.ui.Modifier.align(Alignment.BottomCenter),
            input = input,
            isActive = input.isNotBlank(),
            webSearchEnabled = webSearchEnabled,
            onWebSearchEnabledChange = onWebSearchEnabledChange,
            onInputChange = onInputChange,
            onSearch = onSearch,
            onClear = onClear,
            // retained by scaffold
            onNewSession = {},
            showSearchBar = showSearchBar,
            onShowSearchBarChange = onShowSearchBarChange,
            onInputFocusChanged = onInputFocusChanged,
        )
    }
}
