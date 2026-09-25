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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 加载状态指示器
 */
@Composable
internal fun ResponseLoadingIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    )
}

/**
 * Markdown 格式文本展示
 */
@Composable
internal fun ResponseMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    MarkdownTextView(markdown = markdown, modifier = modifier.fillMaxWidth())
}

/**
 * 带注解的可点击文本（支持 Citation + URL 链接）
 */
@Composable
internal fun ResponseAnnotatedTextContent(
    annotatedText: AnnotatedString,
    bodyStyle: TextStyle,
    onCitationClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val headingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    val structuredText = remember(annotatedText, headingColor) {
        buildStructuredAnnotatedText(
            source = annotatedText,
            headingColor = headingColor
        )
    }
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    Text(
        text = structuredText,
        style = bodyStyle,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val lr = layoutResult ?: return@detectTapGestures
                    val offset = lr.getOffsetForPosition(pos)
                    val citation = structuredText.getStringAnnotations(tag = "citation", start = offset, end = offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                    if (citation != null && onCitationClick != null) {
                        onCitationClick(citation)
                        return@detectTapGestures
                    }
                    val url = structuredText.getStringAnnotations(tag = "url", start = offset, end = offset)
                        .firstOrNull()
                        ?.item
                    if (!url.isNullOrBlank()) {
                        try { uriHandler.openUri(url) } catch (_: Throwable) {}
                    }
                }
            },
        onTextLayout = { layoutResult = it }
    )
}

/**
 * 带光标动画的流式输出文本（用于实时流场景）
 */
@Composable
internal fun ResponseStreamingTextWithCursor(
    text: String,
    bodyStyle: TextStyle,
    cursorAlpha: Float,
    modifier: Modifier = Modifier
) {
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = cursorAlpha)
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = bodyStyle,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { layoutResult = it }
        )

        val lr = layoutResult
        if (lr != null) {
            val cursorRect = try {
                lr.getCursorRect(text.length)
            } catch (_: Throwable) {
                null
            }

            if (cursorRect != null) {
                val densityLocal = LocalDensity.current
                val w = with(densityLocal) { 2.dp.toPx() }
                val corner = with(densityLocal) { 1.dp.toPx() }
                val offsetRight = with(densityLocal) { 2.dp.toPx() }
                val h = cursorRect.height * 0.8f
                val left = cursorRect.right + offsetRight
                val top = cursorRect.top + (cursorRect.height - h) / 2f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = cursorColor.copy(alpha = (cursorAlpha * 0.12f)),
                        topLeft = Offset(left - 2f, top - 2f),
                        size = Size(width = w + 4f, height = h + 4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + 1f, corner + 1f)
                    )
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
}

/**
 * 纯文本展示（无注解）
 */
@Composable
internal fun ResponsePlainTextContent(
    text: String,
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val headingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    val structuredText = remember(text, headingColor) {
        buildStructuredAnnotatedText(
            source = AnnotatedString(text),
            headingColor = headingColor
        )
    }
    Text(text = structuredText, style = bodyStyle, modifier = modifier)
}

private val structuralHeadingRegex = Regex("^(结论|建议|提示|说明|依据|注意|备注)[:：]?\\s*(.*)$")
private val structuralListRegex = Regex("^(\\d+[.、]|[一二三四五六七八九十]+、|（[一二三四五六七八九-9]+）|\\([一二三四五六七八九-9]+\\))\\s*(.*)$")

private fun buildStructuredAnnotatedText(
    source: AnnotatedString,
    headingColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    if (source.text.isBlank()) return source

    return buildAnnotatedString {
        var cursor = 0
        val text = source.text

        while (cursor < text.length) {
            val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
            val rawLine = text.substring(cursor, lineEnd)
            val lineSource = source.subSequence(cursor, lineEnd)

            appendStructuredLine(
                rawLine = rawLine,
                lineSource = lineSource,
                headingColor = headingColor
            )

            if (lineEnd < text.length) {
                append("\n")
            }
            cursor = lineEnd + 1
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStructuredLine(
    rawLine: String,
    lineSource: AnnotatedString,
    headingColor: androidx.compose.ui.graphics.Color
) {
    val headingMatch = structuralHeadingRegex.find(rawLine)
    if (headingMatch != null) {
        val label = headingMatch.groupValues[1]
        var remainderStart = label.length
        while (remainderStart < rawLine.length && (rawLine[remainderStart] == ':' || rawLine[remainderStart] == '：' || rawLine[remainderStart].isWhitespace())) {
            remainderStart++
        }
        withStyle(
            SpanStyle(
                color = headingColor,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(label)
        }
        if (remainderStart < rawLine.length) {
            append("\n")
            append(lineSource.subSequence(remainderStart, lineSource.length))
        }
        return
    }

    val listMatch = structuralListRegex.find(rawLine)
    if (listMatch != null) {
        val marker = listMatch.groupValues[1]
        var remainderStart = marker.length
        while (remainderStart < rawLine.length && rawLine[remainderStart].isWhitespace()) {
            remainderStart++
        }
        withStyle(
            SpanStyle(
                color = headingColor,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(marker)
        }
        if (remainderStart < rawLine.length) {
            append(" ")
            append(lineSource.subSequence(remainderStart, lineSource.length))
        }
        return
    }

    append(lineSource)
}

/**
 * Copy / Retry 操作按钮
 */
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
    modifier: Modifier = Modifier
) {
    if (text.isNotEmpty()) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    onCopy()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                if (onThumbUp != null) {
                    Spacer(modifier = Modifier.width(18.dp))
                    IconButton(onClick = onThumbUp, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "点赞",
                            tint = if (isThumbUpSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
                if (onThumbDown != null) {
                    Spacer(modifier = Modifier.width(18.dp))
                    IconButton(onClick = onThumbDown, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = "点踩",
                            tint = if (isThumbDownSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
                if (allowRetry) {
                    Spacer(modifier = Modifier.width(18.dp))
                    IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新生成",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                if (onShowSources != null) {
                    Spacer(modifier = Modifier.width(18.dp))
                    IconButton(onClick = onShowSources, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "查看来源",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
