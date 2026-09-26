package com.example.powerai.ui.screen.database

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseFocusTarget
import com.example.powerai.domain.model.DatabaseRow
import com.example.powerai.ui.component.LazyListScrollAnchor
import com.example.powerai.util.PdfSourceRef

/**
 * Pure navigation/lookup helpers for [DatabaseScreen]: anchors, fallback
 * scoring, collapsed-group resolution, meta lines and stable list keys.
 *
 * These functions were extracted from DatabaseScreen.kt (file placement and
 * named constants for scoring/label values, no behaviour change) so they can
 * be characterized and tested without a Compose environment.
 */

private const val TITLE_MATCH_SCORE = 10
private const val GROUP_NAME_MATCH_SCORE = 8
private const val SOURCE_MATCH_SCORE = 6
private const val PAGE_MATCH_SCORE = 4
private const val CONTENT_PREFIX_MATCH_SCORE = 3
private const val CONTENT_PREFIX_LENGTH = 24
private const val ANCHOR_LABEL_MAX_LENGTH = 14

internal data class DatabaseItemLocation(
    val groupKey: String,
    val listIndex: Int,
)

internal fun findDatabaseItemLocation(
    groups: List<DatabaseFileGroup>,
    target: DatabaseFocusTarget,
    collapsedGroupKeys: Set<String>,
): DatabaseItemLocation? {
    var listIndex = 0
    groups.forEach { group ->
        listIndex += 1
        val rowIndex = group.rows.indexOfFirst { row -> row.item.id == target.itemId }
        if (rowIndex >= 0) {
            return DatabaseItemLocation(
                groupKey = group.key,
                listIndex = listIndex + rowIndex,
            )
        }
        if (!collapsedGroupKeys.contains(group.key)) {
            listIndex += group.rows.size
        }
    }

    val fallbackMatches =
        groups.flatMap { group ->
            group.rows.mapIndexedNotNull { rowIndex, row ->
                val score = fallbackMatchScore(group, row, target)
                if (score <= 0) {
                    null
                } else {
                    Triple(
                        group.key,
                        listIndexFor(groups, group.key, rowIndex, collapsedGroupKeys),
                        score,
                    )
                }
            }
        }
    val best = fallbackMatches.maxByOrNull { it.third }
    return best?.let { DatabaseItemLocation(groupKey = it.first, listIndex = it.second) }
}

internal fun findDatabaseItemLocation(
    groups: List<DatabaseFileGroup>,
    itemId: Long,
    collapsedGroupKeys: Set<String>,
): DatabaseItemLocation? {
    if (itemId <= 0L) return null
    var listIndex = 0
    groups.forEach { group ->
        listIndex += 1
        val rowIndex = group.rows.indexOfFirst { row -> row.item.id == itemId }
        if (rowIndex >= 0) {
            return DatabaseItemLocation(
                groupKey = group.key,
                listIndex = listIndex + rowIndex,
            )
        }
        if (!collapsedGroupKeys.contains(group.key)) {
            listIndex += group.rows.size
        }
    }
    return null
}

internal fun findDatabaseGroupHeaderIndex(
    groups: List<DatabaseFileGroup>,
    targetGroupKey: String,
    collapsedGroupKeys: Set<String>,
): Int? {
    var listIndex = 0
    groups.forEach { group ->
        if (group.key == targetGroupKey) {
            return listIndex
        }
        listIndex += 1
        if (!collapsedGroupKeys.contains(group.key)) {
            listIndex += group.rows.size
        }
    }
    return null
}

internal fun listIndexFor(
    groups: List<DatabaseFileGroup>,
    targetGroupKey: String,
    targetRowIndex: Int,
    collapsedGroupKeys: Set<String>,
): Int {
    var listIndex = 0
    groups.forEach { group ->
        listIndex += 1
        if (group.key == targetGroupKey) {
            return listIndex + targetRowIndex
        }
        if (!collapsedGroupKeys.contains(group.key)) {
            listIndex += group.rows.size
        }
    }
    return 0
}

internal fun fallbackMatchScore(
    group: DatabaseFileGroup,
    row: DatabaseRow,
    target: DatabaseFocusTarget,
): Int {
    val item = row.item
    var score = 0

    val normalizedTargetTitle = normalizeDbMatchText(target.title)
    val normalizedItemTitle = normalizeDbMatchText(item.title)
    if (normalizedTargetTitle.isNotBlank() && normalizedTargetTitle == normalizedItemTitle) {
        score += TITLE_MATCH_SCORE
    }

    val normalizedTargetSource = normalizeDbMatchText(target.source)
    val normalizedGroupName = normalizeDbMatchText(group.fileName)
    val normalizedItemSource = normalizeDbMatchText(PdfSourceRef.userVisibleSource(item.source))
    if (normalizedTargetSource.isNotBlank()) {
        if (normalizedTargetSource.contains(normalizedGroupName) || normalizedGroupName.contains(normalizedTargetSource)) {
            score += GROUP_NAME_MATCH_SCORE
        }
        if (normalizedTargetSource.contains(normalizedItemSource) || normalizedItemSource.contains(normalizedTargetSource)) {
            score += SOURCE_MATCH_SCORE
        }
    }

    if (target.pageNumber != null && target.pageNumber == item.pageNumber) {
        score += PAGE_MATCH_SCORE
    }

    val normalizedTargetContent = normalizeDbMatchText(target.content)
    val normalizedItemContent = normalizeDbMatchText(item.content)
    if (normalizedTargetContent.isNotBlank() && normalizedItemContent.isNotBlank()) {
        val prefix = normalizedTargetContent.take(CONTENT_PREFIX_LENGTH)
        if (prefix.isNotBlank() && normalizedItemContent.contains(prefix)) {
            score += CONTENT_PREFIX_MATCH_SCORE
        }
    }

    return score
}

internal fun normalizeDbMatchText(value: String?): String {
    return value
        .orEmpty()
        .replace(Regex("\\s+"), "")
        .replace("·", "")
        .replace("|", "")
        .trim()
}

internal fun buildDatabaseAnchors(
    groups: List<DatabaseFileGroup>,
    collapsedGroupKeys: Set<String>,
): List<LazyListScrollAnchor> {
    var itemIndex = 0
    return buildList {
        groups.forEach { group ->
            add(
                LazyListScrollAnchor(
                    itemIndex = itemIndex,
                    label = shortAnchorLabel(group.fileName.ifBlank { "未命名文" }),
                    fullLabel = group.fileName.ifBlank { "未命名文" },
                ),
            )
            itemIndex += 1
            if (!collapsedGroupKeys.contains(group.key)) {
                itemIndex += group.rows.size
            }
        }
    }
}

internal fun shortAnchorLabel(fileName: String): String {
    return if (fileName.length <= ANCHOR_LABEL_MAX_LENGTH) fileName else fileName.take(ANCHOR_LABEL_MAX_LENGTH) + "..."
}

internal fun buildDatabaseBubbleLabel(
    currentIndex: Int,
    currentAnchor: LazyListScrollAnchor?,
    anchors: List<LazyListScrollAnchor>,
): String {
    val anchor = currentAnchor ?: return "第${currentIndex + 1}项"
    val relativeIndex = (currentIndex - anchor.itemIndex).coerceAtLeast(0)
    val nextAnchorIndex =
        anchors
            .firstOrNull { it.itemIndex > anchor.itemIndex }
            ?.itemIndex
            ?: Int.MAX_VALUE
    return when {
        relativeIndex <= 0 -> anchor.fullLabel
        currentIndex >= nextAnchorIndex -> anchor.fullLabel
        else -> "${anchor.fullLabel} · 第${relativeIndex}条命中"
    }
}

internal fun resolveCollapsedGroupKeys(
    existing: Set<String>,
    currentKeys: Set<String>,
    isSearching: Boolean,
    newestKey: String?,
    keepExpandedKey: String?,
): Set<String> {
    val pruned = existing.intersect(currentKeys)
    if (isSearching) return emptySet()
    if (pruned.isNotEmpty()) return keepExpandedKey?.let { pruned - it } ?: pruned
    val newest = newestKey ?: return pruned
    val defaultCollapsed = currentKeys - newest
    return keepExpandedKey?.let { defaultCollapsed - it } ?: defaultCollapsed
}

internal fun emptyStateMessage(isSearching: Boolean): String {
    return if (isSearching) "未找到相关结果" else "数据库为空"
}

internal fun buildGroupHitMeta(
    isSearching: Boolean,
    searchQuery: String,
    hitCount: Int,
    imageCount: Int,
): AnnotatedString {
    return if (isSearching) {
        buildAnnotatedString {
            withStyle(style = SpanStyle(color = SearchHitYellow)) {
                append("本次命中")
                append(searchQuery)
                append("$hitCount · 关联截图 $imageCount ")
            }
        }
    } else {
        AnnotatedString("当前显示 $hitCount · 关联截图 $imageCount")
    }
}

internal fun buildItemMetaLine(row: DatabaseRow): String {
    val item = row.item
    return buildString {
        if (row.imagesCount > 0) append("截图 ${row.imagesCount} · ")
        PdfSourceRef.userVisibleSource(item.source).takeIf { it.isNotBlank() }?.let { append(it) }
        item.pageNumber?.let { append(" · p$it") }
        if (item.category.isNotBlank()) append(" · ${item.category}")
    }
}

/** Stable LazyColumn key for a group header row. */
internal fun databaseGroupHeaderKey(groupKey: String): String = "header::$groupKey"

/** Stable LazyColumn key for an item row; unique within the group. */
internal fun databaseRowKey(
    groupKey: String,
    itemId: Long,
): String = "row::$groupKey::$itemId"

internal val SearchHitYellow = Color(0xFFFFC107)
