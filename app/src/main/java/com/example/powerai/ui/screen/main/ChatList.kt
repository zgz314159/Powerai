package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.chat.ChatTurn
import kotlinx.coroutines.launch

@Composable
internal fun ChatList(
    turns: List<ChatTurn>,
    currentTurnId: Long?,
    isStreaming: Boolean,
    turnRetryAllowed: Map<Long, Boolean>,
    inputActive: Boolean,
    onInputChange: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()

    // delegate the scrolling behavior to a helper composable
    AutoScrollOnStreaming(
        listState = listState,
        turns = turns,
        currentTurnId = currentTurnId,
        isStreaming = isStreaming
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 88.dp,
                bottom = if (inputActive) 120.dp else 100.dp
            )
        ) {
            val shouldShowEmptyHint = turns.isEmpty() && !isStreaming
            if (shouldShowEmptyHint) {
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = "请输入问题以开始对",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(turns, key = { _, t -> t.id }) { _, t ->
                val loadingThis = isStreaming && t.id == currentTurnId
                TypingAiResponseCard(
                    userMessage = t.question,
                    text = t.answer,
                    isLoading = loadingThis,
                    askedAtMillis = t.askedAtMillis,
                    sources = t.sources,
                    onCopy = { onCopy(t.answer) },
                    onRetry = {
                        onInputChange(t.question)
                        onRetry()
                    },
                    allowRetry = turnRetryAllowed[t.id] ?: false,
                    renderMarkdownWhenPossible = true
                )
            }
        }
    }
}
