package com.example.powerai.ui.screen.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Streaming content presentation shared by [ResponseBody]:
 * the loading indicator and the streaming text with an animated cursor.
 * Structured text lives in ResponseStructuredText.kt, the action row in
 * ResponseActionRow.kt and citation content in ResponseCitationContent.kt.
 */

/**
 * 加载状态指示器
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ResponseLoadingIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier =
            modifier
                .fillMaxWidth()
                .height(2.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    )
}

/**
 * 带光标动画的流式输出文本（用于实时流场景）
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ResponseStreamingTextWithCursor(
    text: String,
    bodyStyle: TextStyle,
    cursorAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = cursorAlpha)
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = bodyStyle,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { layoutResult = it },
        )

        val lr = layoutResult
        if (lr != null) {
            val cursorRect =
                try {
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
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + 1f, corner + 1f),
                    )
                    drawRoundRect(
                        color = cursorColor.copy(alpha = cursorAlpha),
                        topLeft = Offset(left, top),
                        size = Size(width = w, height = h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                    )
                }
            }
        }
    }
}
