package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.screen.hybrid.AiUiState
import com.example.powerai.ui.theme.HighlightYellow
import com.example.powerai.util.PdfSourceRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原来位于 `MainPages.kt`
 *
 * 在拆分过程中保持了所有原有参数和行为，便于逐步迁移
 */
@Composable
fun KnowledgeResultList(
    items: List<KnowledgeItem>,
    expandedItemId: Long?,
    onToggleExpand: (Long) -> Unit,
    highlight: String = "",
    metaProvider: ((KnowledgeItem) -> String?)? = null,
    onItemClick: ((KnowledgeItem) -> Unit)? = null,
    topContent: (@Composable () -> Unit)? = null,
    animateItems: Boolean = true,
    showPagination: Boolean = true,
    currentPage: Int = 1,
    totalPages: Int? = null,
    hasPrev: Boolean = false,
    hasNext: Boolean = false,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    showEmptyState: Boolean = true,
    isPageLoading: Boolean = false,
    pageSize: Int = 4
) {
    val shouldAnimate = animateItems && showPagination && (hasPrev || hasNext)

    val displayed = remember(items, showPagination, hasPrev, hasNext, currentPage, pageSize) {
        if (items.isEmpty()) return@remember emptyList()
        if (showPagination && (hasPrev || hasNext)) {
            val start = ((currentPage - 1).coerceAtLeast(0)) * pageSize
            items.drop(start).take(pageSize)
        } else {
            items.take(pageSize)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 88.dp,
            bottom = 4.dp
        )
    ) {
        topContent?.let { tc ->
            item {
                Box(modifier = Modifier.fillMaxWidth()) { tc() }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (displayed.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isPageLoading -> CircularProgressIndicator()
                        showEmptyState -> EmptyState(text = "未找到相关结")
                    }
                }
            }
        } else {
            itemsIndexed(
                items = displayed,
                key = { index, item ->
                    if (item.id > 0L) {
                        "kid:${item.id}"
                    } else {
                        "idx:$index|src:${item.source}|title:${item.title.hashCode()}|content:${item.content.hashCode()}"
                    }
                }
            ) { index, itemData ->
                val previewLines = if (itemData.category == "AI") 10 else 5
                AnimatedFadeInItem(index = index, enabled = shouldAnimate) {
                    KnowledgeItemCard(
                        item = itemData,
                        highlight = highlight,
                        metaLine = metaProvider?.invoke(itemData),
                        expanded = if (onItemClick != null) false else expandedItemId == itemData.id,
                        onClick = if (onItemClick != null) ({ onItemClick(itemData) }) else null,
                        onExpand = if (onItemClick != null) null else ({ onToggleExpand(itemData.id) }),
                        previewMaxLines = previewLines
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (showPagination && items.isNotEmpty() && !isPageLoading && (hasPrev || hasNext || (totalPages != null && totalPages > 1))) {
                item {
                    PaginationFooter(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        onPrev = onPrev,
                        onNext = onNext
                    )
                }
            }
        }
    }
}

