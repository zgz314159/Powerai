package com.example.powerai.ui.screen.main

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.powerai.domain.model.chat.ChatTurn
import kotlinx.coroutines.delay

private suspend fun settleToBottom(listState: LazyListState, lastIndex: Int) {
    runCatching { listState.scrollToItem(lastIndex) }
    delay(24)
    runCatching {
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
        val visibleLastIndex = lastVisible?.index ?: -1
        if (visibleLastIndex < lastIndex) {
            listState.scrollToItem(lastIndex)
            return@runCatching
        }
        if (visibleLastIndex == lastIndex && lastVisible != null) {
            val viewportEnd = layoutInfo.viewportEndOffset
            val itemEnd = lastVisible.offset + lastVisible.size
            val overflowPx = (itemEnd - viewportEnd).toFloat()
            if (overflowPx > 1f) {
                listState.scrollBy(overflowPx + 8f)
            }
        }
    }
}

/**
 * Helper that keeps the list scrolled to bottom when new turns arrive or while
 * streaming updates are ongoing. Extracted from [ChatList] to reduce its scope.
 */
@Composable
internal fun AutoScrollOnStreaming(
    listState: LazyListState,
    turns: List<ChatTurn>,
    currentTurnId: Long?,
    isStreaming: Boolean
) {
    var wasStreaming by remember { mutableStateOf(isStreaming) }

    LaunchedEffect(turns) {
        if (turns.isNotEmpty()) {
            runCatching { listState.scrollToItem(turns.size - 1) }
        }
    }

    LaunchedEffect(currentTurnId) {
        if (turns.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(turns.size - 1) }
        }
    }

    LaunchedEffect(isStreaming, turns.size, currentTurnId) {
        val justFinished = wasStreaming && !isStreaming
        if (justFinished && turns.isNotEmpty()) {
            val lastIndex = turns.size - 1
            repeat(5) {
                settleToBottom(listState, lastIndex)
                delay(60)
            }
        }
        wasStreaming = isStreaming
    }

    val lastTurn = turns.lastOrNull()
    val lastTextLen = lastTurn?.answer?.length ?: 0
    var lastScrollAt: Long by remember { mutableStateOf(0L) }
    LaunchedEffect(lastTextLen) {
        if (isStreaming && turns.isNotEmpty()) {
            try {
                val now = System.currentTimeMillis()
                if (now - lastScrollAt < 120L) return@LaunchedEffect
                val lastIndex = turns.size - 1
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                val visibleLast = lastVisible?.index ?: -1
                if (visibleLast < lastIndex) {
                    listState.scrollToItem(lastIndex)
                } else if (visibleLast == lastIndex && lastVisible != null) {
                    val viewportEnd = layoutInfo.viewportEndOffset
                    val itemEnd = lastVisible.offset + lastVisible.size
                    val overflowPx = (itemEnd - viewportEnd).toFloat()
                    if (overflowPx > 1f) {
                        listState.scrollBy(overflowPx + 8f)
                    }
                }
                lastScrollAt = System.currentTimeMillis()
            } catch (_: Throwable) {}
        }
    }
}
