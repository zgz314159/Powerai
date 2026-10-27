@file:Suppress("DEPRECATION", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

// Scaffold now delegates largely to ChatContent and a history drawer wrapper.
// Earlier TODO about splitting input, search, and streaming UI has been addressed with
// dedicated components; further decomposition may be added as needed.

import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import kotlinx.coroutines.launch

@Composable
internal fun AiChatScaffold(
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
    onSelectSession: (Long) -> Unit,
    onNewSession: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    drawerState: DrawerState,
    turnRetryAllowed: Map<Long, Boolean> = emptyMap(),
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val coroutineScope = rememberCoroutineScope()
    var inputFocused by remember { mutableStateOf(false) }
    val inputActive = inputFocused || input.isNotBlank()

    val selectedSessionTurns: List<ChatTurn> =
        remember(sessions, selectedSessionId) {
            val session = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
            session?.turns ?: emptyList()
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AiHistoryDrawerContent(
                sessions = sessions,
                selectedSessionId = selectedSessionId,
                onNewSession = {
                    onNewSession()
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    onSelectSession(id)
                    coroutineScope.launch { drawerState.close() }
                },
            )
        },
    ) {
        ChatContent(
            sessions = sessions,
            selectedSessionId = selectedSessionId,
            currentTurnId = currentTurnId,
            isStreaming = isStreaming,
            webSearchEnabled = webSearchEnabled,
            onWebSearchEnabledChange = onWebSearchEnabledChange,
            input = input,
            onInputChange = onInputChange,
            onClear = onClear,
            onSearch = onSearch,
            onRetry = onRetry,
            onCopy = onCopy,
            turnRetryAllowed = turnRetryAllowed,
            onInputFocusChanged = onInputFocusChanged,
            showSearchBar = showSearchBar,
            onShowSearchBarChange = onShowSearchBarChange,
        )
    }
}

// Drawer contents moved to ChatDrawerContents.kt for clearer separation.

// NOTE: AiHistoryDrawerContent and LocalHistoryDrawerContent have been relocated.

// DatabaseHistoryDrawerContent moved to ChatDrawerContents.kt as part of drawer refactor.
// SmartHistoryDrawerContent relocated as well.
