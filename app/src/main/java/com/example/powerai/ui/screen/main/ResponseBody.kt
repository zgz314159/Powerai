@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ResponseBody(
    text: String,
    isLoading: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    allowRetry: Boolean = false,
    onCitationClick: ((Int) -> Unit)? = null,
    renderMarkdownWhenPossible: Boolean = true
) {
    Spacer(Modifier.height(12.dp))

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    if (isLoading && text.isEmpty()) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    } else {
        val bodyStyle = MaterialTheme.typography.bodyLarge.merge(
            TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
        )
        val citationColor = MaterialTheme.colorScheme.primary
        val linkColor = MaterialTheme.colorScheme.primary

        // performance: derive displayedText from source text so recompositions are minimized
        val displayedText by remember(text) { derivedStateOf { text } }

        // blinking cursor animation (used only during streaming)
        val infiniteTransition = rememberInfiniteTransition()
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = androidx.compose.animation.core.keyframes {
                    durationMillis = 800
                    // 0 - 40%: fully opaque
                    1f at 0
                    1f at 320
                    // 60% - 100%: fade to transparent
                    0f at 800
                },
                repeatMode = RepeatMode.Restart
            )
        )

        val canUseMarkdown = remember(renderMarkdownWhenPossible, isLoading, onCitationClick) {
            renderMarkdownWhenPossible && !isLoading && onCitationClick == null
        }

        val annotated = remember(displayedText, citationColor, linkColor) {
            buildCitedAndLinkedText(displayedText, citationColor, linkColor)
        }

        val hasAnnotations = remember(annotated) {
            annotated.getStringAnnotations(tag = "citation", start = 0, end = annotated.length).isNotEmpty() ||
                annotated.getStringAnnotations(tag = "url", start = 0, end = annotated.length).isNotEmpty()
        }

        if (canUseMarkdown) {
            MarkdownTextView(markdown = displayedText, modifier = Modifier.fillMaxWidth())
        } else if (hasAnnotations) {
            // Use Text + pointerInput + onTextLayout to replace deprecated ClickableText.
            var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
            androidx.compose.material3.Text(
                text = annotated,
                style = bodyStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val lr = layoutResult ?: return@detectTapGestures
                            val offset = lr.getOffsetForPosition(pos)
                            val citation = annotated.getStringAnnotations(tag = "citation", start = offset, end = offset)
                                .firstOrNull()
                                ?.item
                                ?.toIntOrNull()
                            if (citation != null && onCitationClick != null) {
                                onCitationClick(citation)
                                return@detectTapGestures
                            }
                            val url = annotated.getStringAnnotations(tag = "url", start = offset, end = offset)
                                .firstOrNull()
                                ?.item
                            if (!url.isNullOrBlank()) {
                                try { uriHandler.openUri(url) } catch (_: Throwable) {}
                            }
                        }
                    },
                onTextLayout = { layoutResult = it }
            )
        } else {
            // Non-annotated text path. During streaming, append an animated cursor rendered via span style.
            if (isLoading && displayedText.isNotBlank()) {
                // Render text and place a precisely aligned blinking cursor at the logical end
                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = cursorAlpha)
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(
                        text = displayedText,
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth(),
                        onTextLayout = { layoutResult = it }
                    )

                    val lr = layoutResult
                    if (lr != null) {
                        // getCursorRect gives a bounding box for the insertion cursor at the given offset
                        val cursorRect = try {
                            lr.getCursorRect(displayedText.length)
                        } catch (_: Throwable) {
                            null
                        }

                        if (cursorRect != null) {
                            // Precompute density-based sizes in composable scope
                            val densityLocal = LocalDensity.current
                            val w = with(densityLocal) { 2.dp.toPx() }
                            val corner = with(densityLocal) { 1.dp.toPx() }
                            val offsetRight = with(densityLocal) { 2.dp.toPx() }
                            val h = cursorRect.height * 0.8f
                            val left = cursorRect.right + offsetRight
                            val top = cursorRect.top + (cursorRect.height - h) / 2f

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // subtle glow: draw larger, low-alpha rect underneath
                                drawRoundRect(
                                    color = cursorColor.copy(alpha = (cursorAlpha * 0.12f)),
                                    topLeft = Offset(left - 2f, top - 2f),
                                    size = Size(width = w + 4f, height = h + 4f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + 1f, corner + 1f)
                                )

                                // main cursor
                                drawRoundRect(
                                    color = cursorColor.copy(alpha = cursorAlpha),
                                    topLeft = Offset(left, top),
                                    size = Size(width = w, height = h),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                                )
                            }
                        }
                    }
                }
            } else {
                androidx.compose.material3.Text(text = displayedText, style = bodyStyle)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Copy & Retry as small icon buttons (left-aligned, low-opacity)
    if (!isLoading && text.isNotEmpty()) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 8.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                    onCopy()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                if (allowRetry) {
                    Spacer(modifier = Modifier.width(30.dp))
                    IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新生成",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
