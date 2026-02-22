package com.example.powerai.ui.screen.database

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.navigation.Screen
import com.example.powerai.ui.component.KnowledgeItemCard
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
    isActive: Boolean = true
) {
    val collapsedKeysSaver: Saver<Set<String>, List<String>> = remember {
        Saver(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    }
    var collapsedGroupKeys by rememberSaveable(stateSaver = collapsedKeysSaver) { mutableStateOf(emptySet()) }

    // Load data only when this screen is active (visible). This reduces work during
    // tab switches and avoids UI jank caused by composing heavy lists while not visible.
    LaunchedEffect(isActive) {
        if (isActive) viewModel.loadAll()
    }

    val uiState by viewModel.uiState.collectAsState()

    // Default UX: collapse all groups except the newest one (only when not searching).
    LaunchedEffect(uiState.groups, uiState.isLoading, searchQuery) {
        if (uiState.isLoading) return@LaunchedEffect

        val currentKeys = uiState.groups.map { it.key }.toSet()
        if (currentKeys.isEmpty()) return@LaunchedEffect

        // Prune stale keys (e.g. after search results changed).
        val pruned = collapsedGroupKeys.intersect(currentKeys)
        if (pruned != collapsedGroupKeys) {
            collapsedGroupKeys = pruned
        }

        // Apply default only for initial full list (no search) and only if user hasn't toggled yet.
        if (searchQuery.trim().isNotBlank()) return@LaunchedEffect
        if (collapsedGroupKeys.isNotEmpty()) return@LaunchedEffect

        val newestKey = uiState.groups.firstOrNull()?.key ?: return@LaunchedEffect
        collapsedGroupKeys = currentKeys - newestKey
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
                        .padding(start = 16.dp, end = 16.dp, top = 88.dp, bottom = 12.dp)
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
                        val msg = if (searchQuery.trim().isNotBlank()) "未找到相关结果" else "数据库为空"
                        Box(modifier = Modifier.fillMaxSize()) {
                            EmptyState(text = msg)
                        }
                        return@Column
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
                    ) {
                        uiState.groups.forEach { group ->
                            item(key = "header::${group.key}") {
                                val headerMeta = buildString {
                                    append("${group.rows.size}条")
                                    if (group.totalImages > 0) append(" · 截图${group.totalImages}")
                                }

                                val isCollapsed = collapsedGroupKeys.contains(group.key)
                                val toggleLabel = if (isCollapsed) "展开" else "收起"

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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = group.fileName.ifBlank { "未命名文件" },
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
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
                                    items = group.rows,
                                    key = { "row::${group.key}::${it.item.id}" }
                                ) { row ->
                                    val item = row.item
                                    val metaLine = buildString {
                                        if (item.source.isNotBlank()) append(item.source)
                                        item.pageNumber?.let { append(" · 第${it}页") }
                                        if (item.category.isNotBlank()) append(" · ${item.category}")
                                        if (row.imagesCount > 0) append(" · 截图${row.imagesCount}")
                                    }

                                    KnowledgeItemCard(
                                        item = item,
                                        highlight = searchQuery,
                                        metaLine = metaLine,
                                        isSelected = false,
                                        expanded = false,
                                        onClick = {
                                            val encoded = android.net.Uri.encode(searchQuery)
                                            navController.navigate(Screen.Detail.createRoute(item.id, encoded, null, null))
                                        },
                                        modifier = Modifier
                                    )
                                }
                            }
                        }

                        // Extra bottom spacing so the last item isn't covered by floating controls
                        item(key = "bottom_spacer") {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp).height(96.dp))
                        }
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("数据库") }
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
