package com.example.powerai.ui.screen.database

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.model.DatabaseFocusTarget
import com.example.powerai.util.PLog
import kotlinx.coroutines.CoroutineScope

/**
 * Focus coordination for [DatabaseViewModel]: item/group focus, drawer focus
 * restoration and the data contract shared with the R05 navigation helpers
 * ([findDatabaseItemLocation] and friends consume the resulting focus state).
 */
internal class DatabaseFocusCoordinator(
    private val loadCoordinator: DatabaseLoadCoordinator,
    private val state: () -> DatabaseUiState,
    private val reduce: (DatabaseUiState.() -> DatabaseUiState) -> Unit,
    private val clearQuery: () -> Unit,
    private val setCollapsedGroupKeys: (Set<String>) -> Unit,
) {
    private companion object {
        private const val TAG = "PowerAiDbDebug"
    }

    fun focusItem(
        scope: CoroutineScope,
        itemId: Long,
    ) {
        if (itemId <= 0L) return
        reduce { copy(selectedItemId = itemId, pendingSelectedItemScrollId = itemId, focusTarget = DatabaseFocusTarget(itemId)) }
        clearQuery()
        loadCoordinator.loadAll(scope)
    }

    fun focusItem(
        scope: CoroutineScope,
        item: KnowledgeItem,
    ) {
        if (item.id <= 0L) return
        reduce {
            copy(
                selectedItemId = item.id,
                pendingSelectedItemScrollId = item.id,
                focusTarget =
                    DatabaseFocusTarget(
                        itemId = item.id,
                        title = item.title,
                        source = item.contextLabel ?: item.source,
                        pageNumber = item.pageNumber,
                        content = item.content,
                    ),
            )
        }
        loadCoordinator.prefetchSourceFileNames(scope, listOf(item))
        clearQuery()
        loadCoordinator.loadAll(scope)
    }

    fun focusGroup(groupKey: String) {
        val normalized = groupKey.trim()
        if (normalized.isBlank()) return
        reduce { copy(selectedItemId = null, pendingSelectedItemScrollId = null, focusTarget = null) }
        val currentKeys = state().groups.map { it.key }.toSet()
        if (currentKeys.isNotEmpty() && currentKeys.contains(normalized)) {
            setCollapsedGroupKeys(currentKeys - normalized)
        }
        reduce { copy(focusGroupKey = normalized) }
    }

    fun focusGroupFromDrawer(
        scope: CoroutineScope,
        groupKey: String,
    ) {
        val normalized = groupKey.trim()
        if (normalized.isBlank()) return
        val directoryCount = loadCoordinator.directoryGroups.value.size
        PLog.d(
            TAG,
            "focusGroupFromDrawer groupKey=$normalized directoryGroups=$directoryCount " +
                "uiGroups=${state().groups.size}",
        )
        reduce { copy(selectedItemId = null, pendingSelectedItemScrollId = null, focusTarget = null) }
        clearQuery()

        val directoryGroups = loadCoordinator.directoryGroups.value
        if (directoryGroups.isNotEmpty()) {
            val groupKeys = directoryGroups.map { it.key }.toSet()
            val containsTarget = groupKeys.contains(normalized)
            reduce { copy(isLoading = false, groups = directoryGroups, errorMessage = null) }
            PLog.d(
                TAG,
                "focusGroupFromDrawer restored directoryGroups=${directoryGroups.size} " +
                    "containsTarget=$containsTarget",
            )
            if (containsTarget) {
                setCollapsedGroupKeys(groupKeys - normalized)
            }
            reduce { copy(focusGroupKey = normalized) }
            return
        }

        reduce { copy(focusGroupKey = normalized) }
        PLog.w(TAG, "focusGroupFromDrawer directoryGroups empty, falling back to loadAllInternal for group=$normalized")
        loadCoordinator.loadAll(scope)
    }
}
