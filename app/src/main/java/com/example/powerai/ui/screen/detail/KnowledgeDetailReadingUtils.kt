package com.example.powerai.ui.screen.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal fun scaledSp(value: Float, fontScale: Float): androidx.compose.ui.unit.TextUnit {
    return (value * fontScale.coerceIn(0.75f, 3.0f)).sp
}

@Composable
internal fun DetailCopyAction(
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    val actionTint = if (copied) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(2000)
        copied = false
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable {
                copied = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCopy()
            }
            .background(
                if (copied) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                }
            )
            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .defaultMinSize(minHeight = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = copied,
                label = "detail-copy-icon"
            ) { isCopied ->
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (isCopied) "已复" else "复制全文",
                    tint = actionTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = "复制全文",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            ),
            color = actionTint
        )
    }
}

@Composable
internal fun DetailMicroToast(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.86f)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}
