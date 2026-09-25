package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.theme.HighlightYellow
import com.example.powerai.util.PdfSourceRef

private data class FileGroup(
    val key: String,
    val fileName: String,
    val items: List<KnowledgeItem>
)

/**
 * `MainPages.kt` 拆出LocalResultsPage
 * 已重构：提取 helper 方法和子 Composable，降低主函数复杂度
 */
@Composable
fun LocalResultsPage(
    localResults: List<KnowledgeItem>,
    expandedItemId: Long?,
    onToggleExpand: (Long) -> Unit,
    highlight: String,
    metaProvider: ((KnowledgeItem) -> String?)? = null,
    onOpenDetail: (id: Long, blockIndex: Int?, blockId: String?, highlight: String?) -> Unit,
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

    val safeResults = remember(localResults) {
        applySafeFiltering(localResults)
    }

    val groups: List<FileGroup> = remember(safeResults) {
        buildFileGroups(localResults)
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
                        showEmptyState -> EmptyState(text = "未找到相关结")
                    }
                }
            }
            return@LazyColumn
        }

        groups.forEach { group ->
            item(key = "header::${group.key}") {
                FileGroupHeader(
                    group = group,
                    isCollapsed = collapsedGroupKeys.contains(group.key),
                    onToggle = { key ->
                        collapsedGroupKeys = if (collapsedGroupKeys.contains(key)) {
                            collapsedGroupKeys - key
                        } else {
                            collapsedGroupKeys + key
                        }
                    }
                )
            }

            if (!collapsedGroupKeys.contains(group.key)) {
                items(
                    count = group.items.size,
                    // include index to guarantee uniqueness even if IDs repeat
                    key = { idx -> "row::${group.key}::${group.items[idx].id}::$idx" }
                ) { idx ->
                    val item = group.items[idx]
                    KnowledgeItemCard(
                        item = item,
                        highlight = highlight,
                        highlightStyleOverride = localHighlightStyle,
                        metaLine = metaProvider?.invoke(item),
                        onClick = {
                            if (item.id > 0) onOpenDetail(
                                item.id,
                                item.hitBlockIndex,
                                item.hitBlockId,
                                item.highlightHint?.takeIf { it.isNotBlank() } ?: highlight
                            )
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

private fun applySafeFiltering(results: List<KnowledgeItem>): List<KnowledgeItem> {
    return results.distinctBy { it.content }.take(10)
}

private fun buildFileGroups(results: List<KnowledgeItem>): List<FileGroup> {
    if (results.isEmpty()) return emptyList()
    
    val ordered = LinkedHashMap<String, MutableList<KnowledgeItem>>()
    val names = LinkedHashMap<String, String>()
    
    for (item in results) {
        val ref = PdfSourceRef.parse(item.source)
        val key = ref?.fileId ?: PdfSourceRef.display(item.source).ifBlank { 
            item.source.ifBlank { "__unknown__" } 
        }
        val name = ref?.fileName ?: PdfSourceRef.display(item.source).ifBlank { "未命名文" }
        names.putIfAbsent(key, name)
        val list = ordered.getOrPut(key) { ArrayList() }
        list.add(item)
    }
    
    return ordered.entries.map { (key, list) ->
        FileGroup(key = key, fileName = names[key].orEmpty(), items = list)
    }
}

private fun groupToggleLabel(isCollapsed: Boolean): String =
    if (isCollapsed) "展开" else "收起"

private fun groupItemCountText(count: Int): String =
    "${count}"

@Composable
private fun FileGroupHeader(
    group: FileGroup,
    isCollapsed: Boolean,
    onToggle: (String) -> Unit
) {
    val toggleLabel = groupToggleLabel(isCollapsed)
    val headerMeta = groupItemCountText(group.items.size)

    Column(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 6.dp)
            .clickable { onToggle(group.key) }
    ) {
        Text(
            text = group.fileName.ifBlank { "未命名文" },
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
