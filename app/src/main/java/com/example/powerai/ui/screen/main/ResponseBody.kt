@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class StreamingMarkdownSplit(
    val stableMarkdown: String,
    val streamingTail: String
)

@Composable
private fun rememberThrottledText(
    text: String,
    enabled: Boolean,
    intervalMs: Long = 100L
): String {
    var renderedText by remember(enabled) { mutableStateOf(text) }
    var lastEmitAt by remember(enabled) { mutableLongStateOf(0L) }

    LaunchedEffect(text, enabled, intervalMs) {
        if (!enabled) {
            renderedText = text
            lastEmitAt = System.currentTimeMillis()
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastEmitAt
        if (elapsed >= intervalMs) {
            renderedText = text
            lastEmitAt = now
        } else {
            delay(intervalMs - elapsed)
            renderedText = text
            lastEmitAt = System.currentTimeMillis()
        }
    }

    return renderedText
}

private fun splitStreamingMarkdown(text: String): StreamingMarkdownSplit {
    if (text.isBlank()) return StreamingMarkdownSplit("", "")

    val lastParagraphBreak = text.lastIndexOf("\n\n")
    if (lastParagraphBreak <= 0) {
        return StreamingMarkdownSplit("", text)
    }

    val stable = text.substring(0, lastParagraphBreak).trimEnd()
    val tail = text.substring(lastParagraphBreak).trimStart('\n')
    return StreamingMarkdownSplit(stable, tail)
}

@Composable
internal fun ResponseBody(
    text: String,
    isLoading: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    allowRetry: Boolean = false,
    onThumbUp: (() -> Unit)? = null,
    onThumbDown: (() -> Unit)? = null,
    isThumbUpSelected: Boolean = false,
    isThumbDownSelected: Boolean = false,
    onShowSources: (() -> Unit)? = null,
    onCitationClick: ((Int) -> Unit)? = null,
    renderMarkdownWhenPossible: Boolean = true,
    supplementalContent: (@Composable () -> Unit)? = null
) {
    Spacer(Modifier.height(12.dp))

    if (isLoading && text.isEmpty()) {
        ResponseLoadingIndicator()
    } else {
        val bodyStyle = MaterialTheme.typography.bodyLarge.merge(
            TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
        )
        val citationColor = MaterialTheme.colorScheme.primary
        val linkColor = MaterialTheme.colorScheme.primary

        val displayedText by remember(text) { derivedStateOf { text } }

        val infiniteTransition = rememberInfiniteTransition()
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 800
                    1f at 0
                    1f at 320
                    0f at 800
                },
                repeatMode = RepeatMode.Restart
            )
        )

        val canUseMarkdown = remember(renderMarkdownWhenPossible, onCitationClick, displayedText, isLoading) {
            renderMarkdownWhenPossible &&
                onCitationClick == null &&
                displayedText.isNotBlank() &&
                !isLoading
        }

        val streamingSplit = remember(displayedText, isLoading) {
            if (isLoading) splitStreamingMarkdown(displayedText)
            else StreamingMarkdownSplit(displayedText, "")
        }

        val streamingStableMarkdown = if (isLoading) streamingSplit.stableMarkdown else ""
        val streamingTailRaw = if (isLoading) {
            if (streamingStableMarkdown.isBlank()) displayedText else streamingSplit.streamingTail
        } else {
            ""
        }
        val streamingTail = rememberThrottledText(
            text = streamingTailRaw,
            enabled = isLoading && streamingTailRaw.isNotBlank(),
            intervalMs = 100L
        )

        val annotated = remember(displayedText, citationColor, linkColor) {
            buildCitedAndLinkedText(displayedText, citationColor, linkColor)
        }

        val hasAnnotations = remember(annotated) {
            annotated.getStringAnnotations(tag = "citation", start = 0, end = annotated.length).isNotEmpty() ||
                annotated.getStringAnnotations(tag = "url", start = 0, end = annotated.length).isNotEmpty()
        }

        // Content rendering delegation
        when {
            isLoading && displayedText.isNotBlank() -> {
                val canRenderStreamingMarkdown = renderMarkdownWhenPossible && onCitationClick == null
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (canRenderStreamingMarkdown && streamingStableMarkdown.isNotBlank()) {
                        ResponseMarkdownContent(
                            markdown = streamingStableMarkdown,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (streamingTail.isNotBlank()) {
                        ResponseStreamingTextWithCursor(
                            text = streamingTail,
                            bodyStyle = bodyStyle,
                            cursorAlpha = cursorAlpha,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            canUseMarkdown -> ResponseMarkdownContent(markdown = displayedText, modifier = Modifier.fillMaxWidth())
            hasAnnotations -> ResponseAnnotatedTextContent(
                annotatedText = annotated,
                bodyStyle = bodyStyle,
                onCitationClick = onCitationClick,
                modifier = Modifier.fillMaxWidth()
            )
            else -> ResponsePlainTextContent(
                text = displayedText,
                bodyStyle = bodyStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (supplementalContent != null) {
            Spacer(Modifier.height(8.dp))
            supplementalContent()
        }
    }

    Spacer(Modifier.height(12.dp))

    if (!isLoading && text.isNotEmpty()) {
        ResponseActionButtons(
            text = text,
            onCopy = onCopy,
            onRetry = onRetry,
            allowRetry = allowRetry,
            onThumbUp = onThumbUp,
            onThumbDown = onThumbDown,
            isThumbUpSelected = isThumbUpSelected,
            isThumbDownSelected = isThumbDownSelected,
            onShowSources = onShowSources
        )
    }
}

