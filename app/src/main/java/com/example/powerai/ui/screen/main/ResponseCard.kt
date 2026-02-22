@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingAiResponseCard(
    userMessage: String? = null,
    text: String,
    isLoading: Boolean,
    askedAtMillis: Long? = null,
    sources: List<String> = emptyList(),
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    allowRetry: Boolean = false,
    onCitationClick: ((Int) -> Unit)? = null,
    renderMarkdownWhenPossible: Boolean = true
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVisible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(askedAtMillis, isVisible) {
        if (askedAtMillis != null && isVisible) {
            while (isVisible) {
                delay(60_000L)
                now = System.currentTimeMillis()
            }
        }
    }

    fun relativeTimeString(timeMillis: Long): String {
        val diff = now - timeMillis
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "刚刚"
            diff < hour -> "${diff / minute}分钟前"
            diff < day -> "${diff / hour}小时前"
            diff < 7 * day -> "${diff / day}天前"
            else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
        }
    }

    // Stream-like conversation layout (no outer card, flat style)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        if (!userMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            // 用户消息靠右显示为气泡，限制最大宽度
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(end = 16.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                        Text(
                            text = userMessage,
                            style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            // 用户时间戳（紧贴气泡，右对齐）
            if (askedAtMillis != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = relativeTimeString(askedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // 在 AI 消息前增加顶部间距，避免挨着用户消息
        Spacer(Modifier.height(16.dp))

        // AI 回答（左对齐），Surface 背景拉满屏幕边缘，文字内容有内边距
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    // 移除 header（保持 ChatGPT 风格），只渲染正文
                    ResponseBody(
                        text = text,
                        isLoading = isLoading,
                        onCopy = onCopy,
                        onRetry = onRetry,
                        allowRetry = allowRetry,
                        onCitationClick = onCitationClick,
                        renderMarkdownWhenPossible = renderMarkdownWhenPossible
                    )
                }
            }
        }

        // Sources list (kept, but inline and flat)
        Spacer(Modifier.height(4.dp))
        SourcesList(sources = sources)
    }
}
