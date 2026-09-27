package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Copy / Retry 操作按钮
 */
@Suppress("LongParameterList", "ktlint:standard:function-naming")
@Composable
internal fun ResponseActionButtons(
    text: String,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    allowRetry: Boolean = false,
    onThumbUp: (() -> Unit)? = null,
    onThumbDown: (() -> Unit)? = null,
    isThumbUpSelected: Boolean = false,
    isThumbDownSelected: Boolean = false,
    onShowSources: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (text.isNotEmpty()) {
        val clipboard = LocalClipboardManager.current
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    onCopy()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                if (onThumbUp != null) {
                    Spacer(modifier = Modifier.width(18.dp))
                    ResponseActionIcon(
                        icon = Icons.Default.ThumbUp,
                        contentDescription = "点赞",
                        selected = isThumbUpSelected,
                        onClick = onThumbUp,
                    )
                }
                if (onThumbDown != null) {
                    Spacer(modifier = Modifier.width(18.dp))
                    ResponseActionIcon(
                        icon = Icons.Default.ThumbDown,
                        contentDescription = "点踩",
                        selected = isThumbDownSelected,
                        onClick = onThumbDown,
                    )
                }
                if (allowRetry) {
                    Spacer(modifier = Modifier.width(18.dp))
                    ResponseActionIcon(
                        icon = Icons.Default.Refresh,
                        contentDescription = "重新生成",
                        selected = false,
                        onClick = onRetry,
                    )
                }
                if (onShowSources != null) {
                    Spacer(modifier = Modifier.width(18.dp))
                    ResponseActionIcon(
                        icon = Icons.Default.Description,
                        contentDescription = "查看来源",
                        selected = false,
                        onClick = onShowSources,
                    )
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ResponseActionIcon(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
        )
    }
}
