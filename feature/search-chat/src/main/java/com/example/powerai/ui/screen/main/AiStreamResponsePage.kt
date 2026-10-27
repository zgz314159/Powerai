package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.domain.model.chat.AiStreamState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.KnowledgeItemCard

/**
 * 专门用于展示流式 AI 响应的页面。原先在 `MainPages.kt` 中。拆分以让各页面职责单一
 */
@Composable
fun AiStreamResponsePage(
    streamState: AiStreamState,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    showEmptyState: Boolean,
    userMessage: String = "",
    askedAtMillis: Long? = null,
    sources: List<String> = emptyList()
) {
    val displayText = remember(streamState) {
        when (streamState) {
            is AiStreamState.Success -> streamState.text
            else -> ""
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            when (streamState) {
                is AiStreamState.Idle -> {
                    if (showEmptyState) {
                        EmptyState(text = "请输入问题以获取 AI 答案")
                    }
                }

                is AiStreamState.Loading -> {
                    TypingAiResponseCard(
                        userMessage = userMessage.takeIf { it.isNotBlank() },
                        text = displayText,
                        isLoading = true,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(displayText) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }

                is AiStreamState.Error -> {
                    TypingAiResponseCard(
                        userMessage = userMessage.takeIf { it.isNotBlank() },
                        text = streamState.message,
                        isLoading = false,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(streamState.message) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }

                is AiStreamState.Success -> {
                    TypingAiResponseCard(
                        userMessage = userMessage.takeIf { it.isNotBlank() },
                        text = displayText,
                        isLoading = isLoading,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(displayText) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
            }
        }
    }
}
