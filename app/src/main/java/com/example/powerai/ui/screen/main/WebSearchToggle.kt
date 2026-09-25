package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Simple toggle between "web search" and "thinking" modes used inside the
 * chat input bar. Extracted to keep ChatInputBar focused on layout logic.
 */
@Composable
internal fun WebSearchToggle(
    webSearchEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row {
        FilterChip(
            selected = webSearchEnabled,
            onClick = { onToggle(true) },
            label = { Text("搜索") }
        )
        Spacer(modifier = Modifier.width(6.dp))
        FilterChip(
            selected = !webSearchEnabled,
            onClick = { onToggle(false) },
            label = { Text("思") }
        )
    }
}
