package com.example.powerai.ui.screen.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@Composable
internal fun KnowledgeDetailMatchNavigationController(
    matchIndices: List<Int>,
    listState: LazyListState
) {
    var currentMatch by remember(matchIndices) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(matchIndices) {
        currentMatch = 0
    }

    if (matchIndices.isEmpty()) return

    KnowledgeDetailMatchNavigator(
        currentMatchIndex = currentMatch,
        totalMatches = matchIndices.size,
        onPrev = {
            val next = KnowledgeDetailMatchIndexing.prev(currentMatch, matchIndices.size)
            currentMatch = next
            scope.launch {
                listState.animateScrollToItemCatching(matchIndices[next])
            }
        },
        onNext = {
            val next = KnowledgeDetailMatchIndexing.next(currentMatch, matchIndices.size)
            currentMatch = next
            scope.launch {
                listState.animateScrollToItemCatching(matchIndices[next])
            }
        }
    )
}
