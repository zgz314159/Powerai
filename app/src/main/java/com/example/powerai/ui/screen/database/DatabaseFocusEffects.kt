package com.example.powerai.ui.screen.database

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.powerai.domain.model.DatabaseFocusTarget

/**
 * Focus/scroll side-effect layer for [DatabaseScreen]: focus target
 * restoration, pending selected-item scrolling, group header positioning and
 * the default collapsed-group policy. Effect bodies were moved verbatim from
 * the screen (visibility and file placement only, no behaviour change).
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DatabaseFocusEffects(
    uiState: DatabaseUiState,
    listState: LazyListState,
    collapsedGroupKeys: Set<String>,
    isSearching: Boolean,
    searchQuery: String,
    viewModel: DatabaseViewModel,
) {
    val focusTarget = uiState.focusTarget
    val focusGroupKey = uiState.focusGroupKey
    val selectedItemId = uiState.selectedItemId
    val pendingSelectedItemScrollId = uiState.pendingSelectedItemScrollId

    DatabaseFocusTargetEffect(uiState, listState, collapsedGroupKeys, focusTarget, viewModel)
    DatabasePendingSelectionEffect(uiState, listState, collapsedGroupKeys, pendingSelectedItemScrollId, viewModel)
    DatabaseFocusGroupEffect(uiState, listState, collapsedGroupKeys, focusGroupKey, viewModel)
    DatabaseDefaultCollapseEffect(
        uiState = uiState,
        collapsedGroupKeys = collapsedGroupKeys,
        isSearching = isSearching,
        searchQuery = searchQuery,
        focusTarget = focusTarget,
        selectedItemId = selectedItemId,
        viewModel = viewModel,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabaseFocusTargetEffect(
    uiState: DatabaseUiState,
    listState: LazyListState,
    collapsedGroupKeys: Set<String>,
    focusTarget: DatabaseFocusTarget?,
    viewModel: DatabaseViewModel,
) {
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
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabasePendingSelectionEffect(
    uiState: DatabaseUiState,
    listState: LazyListState,
    collapsedGroupKeys: Set<String>,
    pendingSelectedItemScrollId: Long?,
    viewModel: DatabaseViewModel,
) {
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
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabaseFocusGroupEffect(
    uiState: DatabaseUiState,
    listState: LazyListState,
    collapsedGroupKeys: Set<String>,
    focusGroupKey: String?,
    viewModel: DatabaseViewModel,
) {
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
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DatabaseDefaultCollapseEffect(
    uiState: DatabaseUiState,
    collapsedGroupKeys: Set<String>,
    isSearching: Boolean,
    searchQuery: String,
    focusTarget: DatabaseFocusTarget?,
    selectedItemId: Long?,
    viewModel: DatabaseViewModel,
) {
    // Default UX: collapse all groups except the newest one (only when not searching).
    LaunchedEffect(uiState.groups, uiState.isLoading, searchQuery, focusTarget, selectedItemId, uiState.focusGroupKey) {
        if (uiState.isLoading) return@LaunchedEffect
        if (uiState.focusGroupKey != null) return@LaunchedEffect

        val currentKeys = uiState.groups.map { it.key }.toSet()
        if (currentKeys.isEmpty()) return@LaunchedEffect

        val targetGroupKey =
            focusTarget
                ?.let { findDatabaseItemLocation(uiState.groups, it, emptySet())?.groupKey }
                ?: selectedItemId?.let { findDatabaseItemLocation(uiState.groups, it, emptySet())?.groupKey }

        viewModel.setCollapsedGroupKeys(
            resolveCollapsedGroupKeys(
                existing = collapsedGroupKeys,
                currentKeys = currentKeys,
                isSearching = isSearching,
                newestKey = uiState.groups.firstOrNull()?.key,
                keepExpandedKey = targetGroupKey,
            ),
        )
    }
}

private fun LazyListState.isItemVisible(index: Int): Boolean {
    return layoutInfo.visibleItemsInfo.any { it.index == index }
}
