package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.SearchBar

/**
 * Bottom input bar used by AI chat screens.
 * 
 * Responsibilities:
 * - Display search input with web search toggle and session controls
 * - Handle user input and search actions
 * - Manage input focus and visibility state
 */
@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    input: String,
    isActive: Boolean,
    webSearchEnabled: Boolean,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onInputChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onNewSession: () -> Unit,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    onInputFocusChanged: (Boolean) -> Unit
) {
    if (!showSearchBar) return
    val submit: () -> Unit = {
        val normalized = input.trim()
        if (normalized.isBlank()) {
            Unit
        } else {
            onSearch(normalized)
            onInputChange("")
            onShowSearchBarChange(false)
            onInputFocusChanged(false)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = if (isActive) 10.dp else 6.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        color = Color.White
    ) {
        SearchBar(
            value = input,
            onValueChange = onInputChange,
            onSearch = submit,
            onClear = {
                // Clear input
                onClear()
            },
            label = "",
            placeholder = "请输入问",
            leading = {
                IconButton(onClick = {
                    onNewSession()
                    onClear()
                }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "新建会话")
                }
            },
            suffix = {
                WebSearchToggle(
                    webSearchEnabled = webSearchEnabled,
                    onToggle = onWebSearchEnabledChange
                )
            },
            trailing = {
                ChatInputActions(
                    input = input,
                    onClear = onClear,
                    onSend = {
                        submit()
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isActive) 64.dp else 56.dp)
                .padding(horizontal = 4.dp, vertical = if (isActive) 2.dp else 0.dp),
            onFocusChanged = { focused ->
                onInputFocusChanged(focused)
            },
            autoFocus = showSearchBar
        )
    }
}
