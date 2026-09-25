package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * Trailing actions shown on the right side of the chat input bar: clear button and
 * send arrow. Split out so ChatInputBar just wires state to them.
 */
@Composable
internal fun ChatInputActions(
    input: String,
    onClear: () -> Unit,
    onSend: () -> Unit
) {
    Row {
        if (input.isNotBlank()) {
            IconButton(onClick = onClear) {
                Icon(imageVector = Icons.Default.Clear, contentDescription = "清空")
            }
        }
        IconButton(
            onClick = {
                if (input.isNotBlank()) onSend()
            }
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "发")
        }
    }
}
