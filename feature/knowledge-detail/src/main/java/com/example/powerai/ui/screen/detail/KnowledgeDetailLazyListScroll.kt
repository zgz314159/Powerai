package com.example.powerai.ui.screen.detail

import androidx.compose.foundation.lazy.LazyListState

internal suspend fun LazyListState.scrollToItemCatching(index: Int) {
    try {
        scrollToItem(index)
    } catch (_: Throwable) {
    }
}

internal suspend fun LazyListState.animateScrollToItemCatching(index: Int) {
    try {
        animateScrollToItem(index)
    } catch (_: Throwable) {
    }
}
