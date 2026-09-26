package com.example.powerai.ui.screen.database


import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseFocusTarget
import com.example.powerai.domain.model.DatabaseRow
import com.example.powerai.navigation.Screen
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.component.LazyListScrollAnchor
import com.example.powerai.ui.component.LazyListScrollIndicator
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.main.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    navController: NavHostController,
    viewModel: DatabaseViewModel = hiltViewModel(),
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onClear: () -> Unit = {},
    showTopSearchBar: Boolean = true,
    showTopBar: Boolean = false,
    isActive: Boolean = true,
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues()
) {
    val logTag = "PowerAiDbDebug"
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    val trimmedSearchQuery = searchQuery.trim()

    // Load data only when this screen is active (visible). This reduces work during
    // tab switches and avoids UI jank caused by composing heavy lists while not visible.
    LaunchedEffect(isActive) {
        if (isActive) viewModel.ensureLoaded()
    }

    val uiState by viewModel.uiState.collectAsState()
    val collapsedGroupKeys = uiState.collapsedGroupKeys
    val focusTarget = uiState.focusTarget
    val focusGroupKey = uiState.focusGroupKey
    val selectedItemId = uiState.selectedItemId
    val pendingSelectedItemScrollId = uiState.pendingSelectedItemScrollId
    val isSearching = trimmedSearchQuery.isNotBlank()
    val groupAnchors = remember(uiState.groups, collapsedGroupKeys) {
        buildDatabaseAnchors(
            groups = uiState.groups,
            collapsedGroupKeys = collapsedGroupKeys
        )
    }

    LaunchedEffect(uiState.isLoading, uiState.errorMessage, uiState.groups.size, searchQuery, isActive) {
        Log.d(
            logTag,
            "DatabaseScreen state isActive=$isActive loading=${uiState.isLoading} error=${uiState.errorMessage} groups=${uiState.groups.size} query=${searchQuery.trim()} sample=${uiState.groups.take(3).joinToString { it.fileName }}"
        )
    }

    LaunchedEffect(uiState.isLoading, uiState.groups, collapsedGroupKeys, focusTarget) {
        val target = focusTarget ?: return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        val location = findDatabaseItemLocation(uiState.groups, target, collapsedGroupKeys)
        if (location == null) {
            viewModel.consumeFocusTarget()
            return@LaunchedEffect
        }
        if (collapsedGroupKeys.contains(location.groupKey)) {
            viewModel.setCollapsedGroupKeys(collapsedGroupKeys - location.groupKey)
            return@LaunchedEffect
        }
        listState.animateScrollToItem(location.listIndex)
        viewModel.consumeFocusTarget()
    }

    LaunchedEffect(uiState.isLoading, uiState.groups, collapsedGroupKeys, pendingSelectedItemScrollId) {
        val selectedId = pendingSelectedItemScrollId ?: return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        val location = findDatabaseItemLocation(uiState.groups, selectedId, collapsedGroupKeys)
        if (location == null) {
            viewModel.consumePendingSelectedItemScroll()
            return@LaunchedEffect
        }
        if (collapsedGroupKeys.contains(location.groupKey)) {
            viewModel.setCollapsedGroupKeys(collapsedGroupKeys - location.groupKey)
            return@LaunchedEffect
        }
        if (!listState.isItemVisible(location.listIndex)) {
            listState.animateScrollToItem(location.listIndex)
        }
        viewModel.consumePendingSelectedItemScroll()
    }

    LaunchedEffect(uiState.isLoading, uiState.groups, collapsedGroupKeys, focusGroupKey) {
        val groupKey = focusGroupKey ?: return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        val headerIndex = findDatabaseGroupHeaderIndex(uiState.groups, groupKey, collapsedGroupKeys)
        if (headerIndex == null) {
            viewModel.consumeFocusGroup()
            return@LaunchedEffect
        }
        if (collapsedGroupKeys.contains(groupKey)) {
            viewModel.setCollapsedGroupKeys(collapsedGroupKeys - groupKey)
            return@LaunchedEffect
        }
        listState.scrollToItem(headerIndex)
        viewModel.consumeFocusGroup()
    }

    // Default UX: collapse all groups except the newest one (only when not searching).
    LaunchedEffect(uiState.groups, uiState.isLoading, searchQuery, focusTarget, selectedItemId, focusGroupKey) {
        if (uiState.isLoading) return@LaunchedEffect
        if (focusGroupKey != null) return@LaunchedEffect

        val currentKeys = uiState.groups.map { it.key }.toSet()
        if (currentKeys.isEmpty()) return@LaunchedEffect

        val targetGroupKey = focusTarget
            ?.let { findDatabaseItemLocation(uiState.groups, it, emptySet())?.groupKey }
            ?: selectedItemId?.let { findDatabaseItemLocation(uiState.groups, it, emptySet())?.groupKey }

        viewModel.setCollapsedGroupKeys(resolveCollapsedGroupKeys(
            existing = collapsedGroupKeys,
            currentKeys = currentKeys,
            isSearching = isSearching,
            newestKey = uiState.groups.firstOrNull()?.key,
            keepExpandedKey = targetGroupKey
        ))
    }

    val content: @Composable (innerPadding: androidx.compose.foundation.layout.PaddingValues) -> Unit = { inner ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                EmptyState(text = "加载失败：${uiState.errorMessage}")
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 12.dp)
                ) {
                    if (showTopSearchBar) {
                        SearchBar(
                            value = searchQuery,
                            onValueChange = { onQueryChange(it) },
                            onSearch = { onSearch() },
                            onClear = { onClear() }
                        )
                    }

                    if (uiState.groups.isEmpty()) {
                        Log.w(logTag, "DatabaseScreen empty-state shown isSearching=$isSearching error=${uiState.errorMessage}")
                        val msg = emptyStateMessage(isSearching)
                        Box(modifier = Modifier.fillMaxSize()) {
                            EmptyState(text = msg)
                        }
                        return@Column
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 6.dp,
                                bottom = 96.dp + innerPadding.calculateBottomPadding()
                            )
                        ) {
                            uiState.groups.forEach { group ->
                                item(key = "header::${group.key}") {
                                    val hitMeta = buildGroupHitMeta(
                                        isSearching = isSearching,
                                        searchQuery = trimmedSearchQuery,
                                        hitCount = group.rows.size,
                                        imageCount = group.totalImages
                                    )
                                    val totalMeta = buildString {
                                        append("总条数 ${group.totalRowsCount} ")
                                        if (group.totalImageCount > 0) append(" · 总截图 ${group.totalImageCount} ")
                                    }

                                    val isCollapsed = collapsedGroupKeys.contains(group.key)
                                    val toggleLabel = if (isCollapsed) "展开" else "收起"

                                    Column(
                                        modifier = Modifier
                                            .padding(top = 12.dp, bottom = 6.dp)
                                            .clickable {
                                                viewModel.toggleCollapsedGroupKey(group.key)
                                            }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = group.fileName.ifBlank { "未命名文" },
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = hitMeta,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSearching) SearchHitYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = totalMeta,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
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
                                        items = group.rows,
                                        key = { "row::${group.key}::${it.item.id}" }
                                    ) { row ->
                                        val item = row.item
                                        val metaLine = buildItemMetaLine(row)

                                        KnowledgeItemCard(
                                            item = item,
                                            highlight = searchQuery,
                                            metaLine = metaLine,
                                            isSelected = selectedItemId == item.id,
                                            expanded = false,
                                            onClick = {
                                                viewModel.setSelectedItem(item.id)
                                                val encoded = android.net.Uri.encode(searchQuery)
                                                navController.navigate(Screen.Detail.createRoute(item.id, encoded, null, null))
                                            },
                                            modifier = Modifier
                                        )
                                    }
                                }
                            }

                            item(key = "bottom_spacer") {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp).height(96.dp))
                            }
                        }

                        LazyListScrollIndicator(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                            anchors = groupAnchors,
                            bubbleLabel = { currentIndex, _, currentAnchor ->
                                buildDatabaseBubbleLabel(
                                    currentIndex = currentIndex,
                                    currentAnchor = currentAnchor,
                                    anchors = groupAnchors
                                )
                            },
                            topPadding = 12.dp,
                            bottomPadding = 100.dp
                        )
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("数据") }
                )
            }
        ) { inner ->
            content(inner)
        }
    } else {
        // When nested inside MainScreen we do not want an extra Scaffold/top bar.
        content(androidx.compose.foundation.layout.PaddingValues())
    }
}

private fun LazyListState.isItemVisible(index: Int): Boolean {
    return layoutInfo.visibleItemsInfo.any { it.index == index }
}
