// No changes made
@file:Suppress("UNUSED_PARAMETER")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.screen.hybrid.AiUiState
import com.example.powerai.ui.theme.HighlightYellow
import com.example.powerai.util.PdfSourceRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Page-level composables extracted from MainScreen for better organization.
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
                        showEmptyState -> EmptyState(text = "未找到相关结果")
                    }
                }
            }
        } else {
            itemsIndexed(
                items = displayed,
                key = { _, it -> it.id }
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

@Composable
fun LocalResultsPage(
    localResults: List<KnowledgeItem>,
    expandedItemId: Long?,
    onToggleExpand: (Long) -> Unit,
    highlight: String,
    metaProvider: ((KnowledgeItem) -> String?)? = null,
    onOpenDetail: (id: Long, blockIndex: Int?, blockId: String?) -> Unit,
    currentPage: Int,
    totalPages: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showEmptyState: Boolean,
    topContent: (@Composable () -> Unit)? = null,
    animateItems: Boolean,
    isPageLoading: Boolean = false,
    pageSize: Int = 4
) {
    val localHighlightStyle = remember {
        SpanStyle(
            background = HighlightYellow,
            color = Color.Unspecified
        )
    }

    data class FileGroup(
        val key: String,
        val fileName: String,
        val items: List<KnowledgeItem>
    )

    val groups: List<FileGroup> = remember(localResults) {
        if (localResults.isEmpty()) return@remember emptyList()
        val ordered = LinkedHashMap<String, MutableList<KnowledgeItem>>()
        val names = LinkedHashMap<String, String>()
        for (item in localResults) {
            val ref = PdfSourceRef.parse(item.source)
            val key = ref?.fileId ?: PdfSourceRef.display(item.source).ifBlank { item.source.ifBlank { "__unknown__" } }
            val name = ref?.fileName ?: PdfSourceRef.display(item.source).ifBlank { "未命名文件" }
            names.putIfAbsent(key, name)
            val list = ordered.getOrPut(key) { ArrayList() }
            list.add(item)
        }
        ordered.entries.map { (key, list) ->
            FileGroup(key = key, fileName = names[key].orEmpty(), items = list)
        }
    }

    val collapsedKeysSaver: Saver<Set<String>, List<String>> = remember {
        Saver(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    }
    var collapsedGroupKeys by rememberSaveable(stateSaver = collapsedKeysSaver) { mutableStateOf(emptySet()) }

    // Keep collapsed keys in sync with current groups.
    LaunchedEffect(groups) {
        val currentKeys = groups.map { it.key }.toSet()
        if (currentKeys.isEmpty()) return@LaunchedEffect
        val pruned = collapsedGroupKeys.intersect(currentKeys)
        if (pruned != collapsedGroupKeys) collapsedGroupKeys = pruned
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
        if (groups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isPageLoading -> CircularProgressIndicator()
                        showEmptyState -> EmptyState(text = "未找到相关结果")
                    }
                }
            }
            return@LazyColumn
        }

        groups.forEach { group ->
            item(key = "header::${group.key}") {
                val isCollapsed = collapsedGroupKeys.contains(group.key)
                val toggleLabel = if (isCollapsed) "展开" else "收起"
                val headerMeta = "${group.items.size}条"
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .clickable {
                            collapsedGroupKeys = if (isCollapsed) {
                                collapsedGroupKeys - group.key
                            } else {
                                collapsedGroupKeys + group.key
                            }
                        }
                ) {
                    Text(
                        text = group.fileName.ifBlank { "未命名文件" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = headerMeta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = toggleLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!collapsedGroupKeys.contains(group.key)) {
                items(
                    count = group.items.size,
                    key = { idx -> "row::${group.key}::${group.items[idx].id}" }
                ) { idx ->
                    val item = group.items[idx]
                    KnowledgeItemCard(
                        item = item,
                        highlight = highlight,
                        highlightStyleOverride = localHighlightStyle,
                        metaLine = metaProvider?.invoke(item),
                        onClick = {
                            if (item.id > 0) onOpenDetail(item.id, item.hitBlockIndex, item.hitBlockId)
                        },
                        expanded = false,
                        onExpand = null,
                        previewMaxLines = 5
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // Extra bottom spacing so that the last results are not covered
        // by floating controls (e.g., bottom search bar in LOCAL/AI modes).
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
fun AiResponsePage(
    aiUiState: AiUiState,
    aiText: String,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    highlight: String = "",
    askedAtMillis: Long? = null,
    sources: List<String> = emptyList(),
    references: List<KnowledgeItem> = emptyList(),
    onOpenEvidenceDetail: ((id: Long, blockIndex: Int?, blockId: String?) -> Unit)? = null,
    showEmptyState: Boolean
) {
    val displayText = remember(aiText) {
        // Keep AI page "无引用"：仅用于展示时去掉 [n] 标记，SMART 页保留原文以支持点击跳证据。
        aiText.replace(Regex("\\[(\\d{1,3})]"), "").replace(Regex("\\s{2,}"), " ").trim()
    }

    val numberedEvidence = remember(references) {
        references.take(10).mapIndexed { idx, item ->
            item.copy(title = "[${idx + 1}] ${item.title}")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            when (aiUiState) {
                is AiUiState.Idle -> {
                    if (showEmptyState) {
                        EmptyState(text = "请输入问题以获取 AI 答案")
                    }
                }
                is AiUiState.Error -> {
                    TypingAiResponseCard(
                        userMessage = highlight.takeIf { it.isNotBlank() },
                        text = aiUiState.message,
                        isLoading = false,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(aiUiState.message) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
                is AiUiState.Loading -> {
                    TypingAiResponseCard(
                        userMessage = highlight.takeIf { it.isNotBlank() },
                        text = displayText,
                        isLoading = true,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(displayText) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
                is AiUiState.Success -> {
                    TypingAiResponseCard(
                        userMessage = highlight.takeIf { it.isNotBlank() },
                        text = displayText,
                        isLoading = false,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(displayText) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
            }
        }

        if (numberedEvidence.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "引用证据", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
            }
            itemsIndexed(
                items = numberedEvidence,
                key = { _, it -> it.id }
            ) { _, item ->
                KnowledgeItemCard(
                    item = item,
                    highlight = highlight,
                    metaLine = buildString {
                        append(item.source)
                        item.pageNumber?.let { append(" · 第${it}页") }
                        item.hitBlockIndex?.let { append(" · 命中块$it") }
                    },
                    onClick = if (onOpenEvidenceDetail != null) {
                        (
                            {
                                if (item.id > 0) onOpenEvidenceDetail(item.id, item.hitBlockIndex, item.hitBlockId)
                            }
                            )
                    } else {
                        null
                    },
                    expanded = false,
                    onExpand = null,
                    previewMaxLines = 5
                )
            }
        }
    }
}

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

@Composable
fun SmartPage(
    aiText: String,
    localResults: List<KnowledgeItem>,
    expandedItemId: Long?,
    onToggleExpand: (Long) -> Unit,
    highlight: String,
    askedAtMillis: Long? = null,
    metaProvider: ((KnowledgeItem) -> String?)? = null,
    onOpenDetail: (id: Long, blockIndex: Int?, blockId: String?) -> Unit,
    onOpenAiDetail: (title: String, content: String) -> Unit,
    onRetry: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    currentPage: Int,
    totalPages: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showEmptyState: Boolean,
    animateItems: Boolean,
    isPageLoading: Boolean = false,
    pageSize: Int = 4
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var selectedCitation by remember { mutableStateOf<Int?>(null) }

    val numberedEvidence = remember(localResults) {
        localResults.take(10).mapIndexed { idx, item ->
            item.copy(title = "[${idx + 1}] ${item.title}")
        }
    }

    LaunchedEffect(selectedCitation) {
        if (selectedCitation != null) {
            delay(1600)
            selectedCitation = null
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 88.dp,
            bottom = 8.dp
        )
    ) {
        item {
            if (aiText.isNotBlank()) {
                TypingAiResponseCard(
                    userMessage = highlight.takeIf { it.isNotBlank() },
                    text = aiText,
                    isLoading = false,
                    askedAtMillis = askedAtMillis,
                    onCopy = { onCopy(aiText) },
                    onRetry = onRetry,
                    allowRetry = false,
                    onCitationClick = { number ->
                        if (number <= 0 || number > numberedEvidence.size) return@TypingAiResponseCard
                        selectedCitation = number
                        val evidenceHeaderIndex = 1
                        val evidenceStartIndex = evidenceHeaderIndex + 1
                        val targetIndex = evidenceStartIndex + (number - 1)
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                )
            } else if (showEmptyState && numberedEvidence.isEmpty() && !isPageLoading) {
                EmptyState(text = "请输入问题以获取智能结果")
            }
        }

        if (numberedEvidence.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "引用证据", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
            }
            itemsIndexed(
                items = numberedEvidence,
                key = { _, it -> it.id }
            ) { index, item ->
                val number = index + 1
                KnowledgeItemCard(
                    item = item,
                    highlight = highlight,
                    metaLine = metaProvider?.invoke(item) ?: buildString {
                        append(item.source)
                        item.pageNumber?.let { append(" · 第${it}页") }
                        item.hitBlockIndex?.let { append(" · 命中块$it") }
                    },
                    isSelected = selectedCitation == number,
                    onClick = {
                        if (item.id > 0) onOpenDetail(item.id, item.hitBlockIndex, item.hitBlockId)
                    },
                    expanded = false,
                    onExpand = null,
                    previewMaxLines = 5
                )
            }
        }
    }
}
