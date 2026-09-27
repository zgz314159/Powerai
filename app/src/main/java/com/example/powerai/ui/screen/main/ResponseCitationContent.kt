package com.example.powerai.ui.screen.main

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

/**
 * Markdown 格式文本展示
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ResponseMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    MarkdownTextView(markdown = markdown, modifier = modifier.fillMaxWidth())
}

/**
 * 带注解的可点击文本（支持 Citation + URL 链接）
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ResponseAnnotatedTextContent(
    annotatedText: AnnotatedString,
    bodyStyle: TextStyle,
    onCitationClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val headingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    val structuredText =
        remember(annotatedText, headingColor) {
            buildStructuredAnnotatedText(
                source = annotatedText,
                headingColor = headingColor,
            )
        }
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Text(
        text = structuredText,
        style = bodyStyle,
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val lr = layoutResult ?: return@detectTapGestures
                        val offset = lr.getOffsetForPosition(pos)
                        val citation =
                            structuredText.getStringAnnotations(tag = "citation", start = offset, end = offset)
                                .firstOrNull()
                                ?.item
                                ?.toIntOrNull()
                        if (citation != null && onCitationClick != null) {
                            onCitationClick(citation)
                            return@detectTapGestures
                        }
                        val url =
                            structuredText.getStringAnnotations(tag = "url", start = offset, end = offset)
                                .firstOrNull()
                                ?.item
                        if (!url.isNullOrBlank()) {
                            try {
                                uriHandler.openUri(url)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                },
        onTextLayout = { layoutResult = it },
    )
}
