package com.example.powerai.ui.screen.database

import android.util.Log
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseRow
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.component.LazyListScrollAnchor
import com.example.powerai.ui.component.LazyListScrollIndicator
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.main.EmptyState

private const val SCREEN_LOG_TAG = "PowerAiDbDebug"

/**
 * Content layer of [DatabaseScreen]: loading/empty/error/content states,
 * optional search bar, the grouped list with stable keys, and the scroll
 * indicator. UI code was moved verbatim from the screen; all event handling
 * is delegated back through callbacks.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DatabaseContent(
    inner: PaddingValues,
    uiState: DatabaseUiState,
    listState: LazyListState,
    groupAnchors: List<LazyListScrollAnchor>,
    searchQuery: String,
    showTopSearchBar: Boolean,
    bottomInset: Dp,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onToggleGroup: (String) -> Unit,
    onItemClick: (KnowledgeItem) -> Unit,
) {
    val trimmedSearchQuery = searchQuery.trim()
    val isSearching = trimmedSearchQuery.isNotBlank()

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.errorMessage != null -> {
            EmptyState(text = "加载失败：${uiState.errorMessage}")
        }

        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 12.dp),
            ) {
                if (showTopSearchBar) {
                    SearchBar(
                        value = searchQuery,
                        onValueChange = { onQueryChange(it) },
                        onSearch = { onSearch() },
                        onClear = { onClear() },
                    )
                }

                if (uiState.groups.isEmpty()) {
                    Log.w(SCREEN_LOG_TAG, "DatabaseScreen empty-state shown isSearching=$isSearching error=${uiState.errorMessage}")
                    val msg = emptyStateMessage(isSearching)
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyState(text = msg)
                    }
                    return@Column
                }

                DatabaseList(
                    uiState = uiState,
                    listState = listState,
                    groupAnchors = groupAnchors,
                    searchQuery = searchQuery,
                    trimmedSearchQuery = trimmedSearchQuery,
                    isSearching = isSearching,
                    bottomInset = bottomInset,
                    onToggleGroup = onToggleGroup,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabaseList(
    uiState: DatabaseUiState,
    listState: LazyListState,
    groupAnchors: List<LazyListScrollAnchor>,
    searchQuery: String,
    trimmedSearchQuery: String,
    isSearching: Boolean,
    bottomInset: Dp,
    onToggleGroup: (String) -> Unit,
    onItemClick: (KnowledgeItem) -> Unit,
) {
    val collapsedGroupKeys = uiState.collapsedGroupKeys
    val selectedItemId = uiState.selectedItemId

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp + bottomInset),
        ) {
            uiState.groups.forEach { group ->
                databaseGroupItems(
                    group = group,
                    isCollapsed = collapsedGroupKeys.contains(group.key),
                    isSearching = isSearching,
                    trimmedSearchQuery = trimmedSearchQuery,
                    searchQuery = searchQuery,
                    selectedItemId = selectedItemId,
                    onToggleGroup = onToggleGroup,
                    onItemClick = onItemClick,
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.padding(top = 16.dp).height(96.dp))
            }
        }

        LazyListScrollIndicator(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            anchors = groupAnchors,
            bubbleLabel = { currentIndex, _, currentAnchor ->
                buildDatabaseBubbleLabel(
                    currentIndex = currentIndex,
                    currentAnchor = currentAnchor,
                    anchors = groupAnchors,
                )
            },
            topPadding = 12.dp,
            bottomPadding = 100.dp,
        )
    }
}

private fun LazyListScope.databaseGroupItems(
    group: DatabaseFileGroup,
    isCollapsed: Boolean,
    isSearching: Boolean,
    trimmedSearchQuery: String,
    searchQuery: String,
    selectedItemId: Long?,
    onToggleGroup: (String) -> Unit,
    onItemClick: (KnowledgeItem) -> Unit,
) {
    item(key = databaseGroupHeaderKey(group.key)) {
        DatabaseGroupHeader(
            group = group,
            isCollapsed = isCollapsed,
            isSearching = isSearching,
            trimmedSearchQuery = trimmedSearchQuery,
            onToggle = { onToggleGroup(group.key) },
        )
    }

    if (!isCollapsed) {
        items(
            items = group.rows,
            key = { databaseRowKey(group.key, it.item.id) },
        ) { row ->
            val item = row.item
            DatabaseRowCard(
                row = row,
                highlight = searchQuery,
                isSelected = selectedItemId == item.id,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabaseGroupHeader(
    group: DatabaseFileGroup,
    isCollapsed: Boolean,
    isSearching: Boolean,
    trimmedSearchQuery: String,
    onToggle: () -> Unit,
) {
    val hitMeta =
        buildGroupHitMeta(
            isSearching = isSearching,
            searchQuery = trimmedSearchQuery,
            hitCount = group.rows.size,
            imageCount = group.totalImages,
        )
    val totalMeta =
        buildString {
            append("总条数 ${group.totalRowsCount} ")
            if (group.totalImageCount > 0) append(" · 总截图 ${group.totalImageCount} ")
        }

    val toggleLabel = if (isCollapsed) "展开" else "收起"

    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp).clickable { onToggle() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.fileName.ifBlank { "未命名文" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hitMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSearching) SearchHitYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = totalMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = toggleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabaseRowCard(
    row: DatabaseRow,
    highlight: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val item = row.item
    val metaLine = buildItemMetaLine(row)

    KnowledgeItemCard(
        item = item,
        highlight = highlight,
        metaLine = metaLine,
        isSelected = isSelected,
        expanded = false,
        onClick = onClick,
        modifier = Modifier,
    )
}
