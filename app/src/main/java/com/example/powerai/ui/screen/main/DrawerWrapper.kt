package com.example.powerai.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Common scaffold for drawer content with a title and optional action button.
 * Actual list items are provided by [content].
 */
@Composable
internal fun DrawerWrapper(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onEdgeAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }

        if (actionLabel != null && onAction != null) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }

        content()
    }
}
