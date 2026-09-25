@file:Suppress("DEPRECATION", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

// Scaffold now delegates largely to ChatContent and a history drawer wrapper. 
// Earlier TODO about splitting input, search, and streaming UI has been addressed with
// dedicated components; further decomposition may be added as needed.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DrawerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.ui.component.SearchBar
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
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    val coroutineScope = rememberCoroutineScope()
    var inputFocused by remember { mutableStateOf(false) }
    val inputActive = inputFocused || input.isNotBlank()

    val selectedSessionTurns: List<ChatTurn> = remember(sessions, selectedSessionId) {
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
                }
            )
        }
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
            onShowSearchBarChange = onShowSearchBarChange
        )
    }
}

// Drawer contents moved to ChatDrawerContents.kt for clearer separation.

// NOTE: AiHistoryDrawerContent and LocalHistoryDrawerContent have been relocated.

// DatabaseHistoryDrawerContent moved to ChatDrawerContents.kt as part of drawer refactor.
// SmartHistoryDrawerContent relocated as well.
