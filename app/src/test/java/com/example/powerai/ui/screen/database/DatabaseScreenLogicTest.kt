package com.example.powerai.ui.screen.database

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseFocusTarget
import com.example.powerai.domain.model.DatabaseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for the pure navigation/search helpers that were
 * extracted from DatabaseScreen's private top-level functions (visibility
 * change only, no behaviour change).
 *
 * Covers item/group locating, fallback scoring, anchors, collapsed groups,
 * meta lines plus empty lists, missing targets and duplicate matches.
 */
class DatabaseScreenLogicTest {
    private fun item(
        id: Long,
        title: String = "title-$id",
        content: String = "content-$id",
        source: String = "src.pdf",
        page: Int? = null,
        category: String = "分类",
    ): KnowledgeItem =
        KnowledgeItem(
            id = id,
            title = title,
            content = content,
            source = source,
            pageNumber = page,
            category = category,
            keywords = emptyList(),
        )

    private fun row(
        item: KnowledgeItem,
        images: Int = 0,
    ): DatabaseRow = DatabaseRow(item = item, imagesCount = images)

    private fun group(
        key: String,
        fileName: String,
        rows: List<DatabaseRow>,
    ): DatabaseFileGroup =
        DatabaseFileGroup(
            key = key,
            fileId = null,
            fileName = fileName,
            totalImages = rows.sumOf { it.imagesCount },
            totalRowsCount = rows.size,
            totalImageCount = rows.sumOf { it.imagesCount },
            rows = rows,
        )

    private val twoGroups =
        listOf(
            group("g1", "手册A.pdf", listOf(row(item(1)), row(item(2)))),
            group("g2", "手册B.pdf", listOf(row(item(3)))),
        )

    @Test
    fun `item location accounts for group headers and expanded rows`() {
        val location = findDatabaseItemLocation(twoGroups, 3L, emptySet())

        assertEquals("g2", location?.groupKey)
        assertEquals(4, location?.listIndex)
    }

    @Test
    fun `item location by id skips collapsed group rows`() {
        val location = findDatabaseItemLocation(twoGroups, 3L, setOf("g1"))

        assertEquals("g2", location?.groupKey)
        assertEquals(2, location?.listIndex)
    }

    @Test
    fun `missing and invalid ids resolve to null`() {
        assertNull(findDatabaseItemLocation(twoGroups, 999L, emptySet()))
        assertNull(findDatabaseItemLocation(twoGroups, 0L, emptySet()))
        assertNull(findDatabaseItemLocation(twoGroups, -5L, emptySet()))
        assertNull(findDatabaseItemLocation(emptyList(), 1L, emptySet()))
        assertNull(findDatabaseItemLocation(emptyList(), DatabaseFocusTarget(itemId = 1L), emptySet()))
    }

    @Test
    fun `target overload falls back to scored matching when id is unknown`() {
        val target =
            DatabaseFocusTarget(
                itemId = 4242L,
                title = "title-2",
                source = null,
                pageNumber = null,
                content = null,
            )

        val location = findDatabaseItemLocation(twoGroups, target, emptySet())

        assertEquals("g1", location?.groupKey)
        assertEquals(2, location?.listIndex)
    }

    @Test
    fun `fallback score accumulates title source page and content evidence`() {
        val longContent = "前缀内容必须匹配这里的一些前缀补充"
        val matchingRow = row(item(7, title = "相同标题", source = "报告.pdf", page = 5, content = longContent))
        val targetGroup = group("g1", "报告.pdf", listOf(matchingRow))
        val target =
            DatabaseFocusTarget(
                itemId = 99L,
                title = "相同标题",
                source = "报告.pdf",
                pageNumber = 5,
                content = "前缀内容必须匹配这里",
            )

        // title 10 + group name 8 + item source 6 + page 4 + content prefix 3
        assertEquals(31, fallbackMatchScore(targetGroup, targetGroup.rows.first(), target))
        assertEquals(0, fallbackMatchScore(targetGroup, targetGroup.rows.first(), DatabaseFocusTarget(itemId = 1L)))
    }

    @Test
    fun `fallback picks the highest score and the first on ties`() {
        val rows =
            listOf(
                row(item(11, title = "相同", page = 9)),
                row(item(12, title = "相同", page = 9)),
                row(item(13, title = "其他")),
            )
        val groups = listOf(group("g1", "a.pdf", rows))
        val target = DatabaseFocusTarget(itemId = 777L, title = "相同", pageNumber = 9)

        val location = findDatabaseItemLocation(groups, target, emptySet())

        // first row wins the tie (both score 14)
        assertEquals(1, location?.listIndex)
        assertEquals("g1", location?.groupKey)
    }

    @Test
    fun `group header index respects collapsed state`() {
        assertEquals(3, findDatabaseGroupHeaderIndex(twoGroups, "g2", emptySet()))
        assertEquals(1, findDatabaseGroupHeaderIndex(twoGroups, "g2", setOf("g1")))
        assertEquals(0, findDatabaseGroupHeaderIndex(twoGroups, "g1", emptySet()))
        assertNull(findDatabaseGroupHeaderIndex(twoGroups, "missing", emptySet()))
        assertNull(findDatabaseGroupHeaderIndex(emptyList(), "g1", emptySet()))
    }

    @Test
    fun `list index helper maps rows and returns zero for unknown groups`() {
        assertEquals(1, listIndexFor(twoGroups, "g1", 0, emptySet()))
        assertEquals(2, listIndexFor(twoGroups, "g1", 1, emptySet()))
        assertEquals(4, listIndexFor(twoGroups, "g2", 0, emptySet()))
        assertEquals(2, listIndexFor(twoGroups, "g2", 0, setOf("g1")))
        assertEquals(0, listIndexFor(twoGroups, "nope", 0, emptySet()))
    }

    @Test
    fun `anchors skip collapsed rows and truncate long labels`() {
        val longName = "一个非常长的文件名称超过十四个字符.pdf"
        val anchors = buildDatabaseAnchors(twoGroups, emptySet())

        assertEquals(2, anchors.size)
        assertEquals(0, anchors[0].itemIndex)
        assertEquals("手册A.pdf", anchors[0].label)
        assertEquals(3, anchors[1].itemIndex)
        assertEquals(longName.take(14) + "...", buildDatabaseAnchors(listOf(group("g3", longName, emptyList())), emptySet())[0].label)

        val collapsed = buildDatabaseAnchors(twoGroups, setOf("g1"))
        assertEquals(0, collapsed[0].itemIndex)
        assertEquals(1, collapsed[1].itemIndex)

        assertEquals("短名", shortAnchorLabel("短名"))
        assertEquals("未命名文", buildDatabaseAnchors(listOf(group("g0", "", emptyList())), emptySet())[0].fullLabel)
        assertTrue(buildDatabaseAnchors(emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `bubble label shows relative hits between anchors`() {
        val anchors = buildDatabaseAnchors(twoGroups, emptySet())

        assertEquals("第5项", buildDatabaseBubbleLabel(4, null, anchors))
        assertEquals("手册A.pdf", buildDatabaseBubbleLabel(0, anchors[0], anchors))
        assertEquals("手册A.pdf · 第1条命中", buildDatabaseBubbleLabel(1, anchors[0], anchors))
        assertEquals("手册A.pdf · 第2条命中", buildDatabaseBubbleLabel(2, anchors[0], anchors))
        // at or past the next anchor the label stays on the anchor name
        assertEquals("手册A.pdf", buildDatabaseBubbleLabel(3, anchors[0], anchors))
    }

    @Test
    fun `collapsed group keys follow search and keep expanded rules`() {
        val keys = setOf("g1", "g2")

        val searching = resolveCollapsedGroupKeys(setOf("g1", "gone"), keys, isSearching = true, newestKey = "g1", keepExpandedKey = null)
        assertTrue(searching.isEmpty())
        val prunedAll = resolveCollapsedGroupKeys(setOf("g1", "g2"), keys, isSearching = false, newestKey = "g1", keepExpandedKey = null)
        assertEquals(setOf("g1", "g2"), prunedAll)
        val keepG1 = resolveCollapsedGroupKeys(setOf("g1", "g2"), keys, isSearching = false, newestKey = "g1", keepExpandedKey = "g1")
        assertEquals(setOf("g2"), keepG1)
        // nothing open yet: everything except the newest group collapses
        val defaultCollapsed = resolveCollapsedGroupKeys(emptySet(), keys, isSearching = false, newestKey = "g1", keepExpandedKey = null)
        assertEquals(setOf("g2"), defaultCollapsed)
        val defaultMinusKeep = resolveCollapsedGroupKeys(emptySet(), keys, isSearching = false, newestKey = "g1", keepExpandedKey = "g1")
        assertEquals(setOf("g2"), defaultMinusKeep)
        val noNewest = resolveCollapsedGroupKeys(emptySet(), keys, isSearching = false, newestKey = null, keepExpandedKey = null)
        assertTrue(noNewest.isEmpty())
    }

    @Test
    fun `empty state message distinguishes search and empty database`() {
        assertEquals("未找到相关结果", emptyStateMessage(isSearching = true))
        assertEquals("数据库为空", emptyStateMessage(isSearching = false))
    }

    @Test
    fun `item meta line composes images source page and category`() {
        val full = buildItemMetaLine(row(item(1, source = "手册.pdf", page = 5, category = "安规"), images = 3))
        assertEquals("截图 3 · 手册.pdf · p5 · 安规", full)

        val bare = buildItemMetaLine(row(item(2, source = "", page = null, category = "")))
        assertEquals("", bare)

        val noImages = buildItemMetaLine(row(item(3, source = "手册.pdf", category = "")))
        assertEquals("手册.pdf", noImages)
    }

    @Test
    fun `group hit meta line switches between search and browse wording`() {
        val searching = buildGroupHitMeta(isSearching = true, searchQuery = "短路", hitCount = 2, imageCount = 1)
        assertEquals("本次命中短路2 · 关联截图 1 ", searching.text)
        assertTrue(searching.spanStyles.isNotEmpty())

        val browsing = buildGroupHitMeta(isSearching = false, searchQuery = "短路", hitCount = 2, imageCount = 1)
        assertEquals("当前显示 2 · 关联截图 1", browsing.text)
        assertFalse(browsing.spanStyles.isNotEmpty())
    }

    @Test
    fun `stable list keys are deterministic and unique across groups`() {
        assertEquals("header::g1", databaseGroupHeaderKey("g1"))
        assertEquals("row::g1::42", databaseRowKey("g1", 42L))
        // recomposition with the same inputs must produce identical keys
        assertEquals(databaseRowKey("g1", 42L), databaseRowKey("g1", 42L))
        // the same item id under another group is a distinct row
        assertNotEquals(databaseRowKey("g1", 42L), databaseRowKey("g2", 42L))
        // header keys and row keys can never collide
        assertNotEquals(databaseGroupHeaderKey("g1"), databaseRowKey("g1", 0L))
        assertFalse(databaseGroupHeaderKey("g1").startsWith("row::"))
        assertFalse(databaseRowKey("g1", 1L).startsWith("header::"))
    }

    @Test
    fun `duplicate item ids resolve to the first group in order`() {
        val duplicated = listOf(
            group("g1", "手册A.pdf", listOf(row(item(5)), row(item(6)))),
            group("g2", "手册B.pdf", listOf(row(item(5)))),
        )

        val byId = findDatabaseItemLocation(duplicated, 5L, emptySet())
        assertEquals("g1", byId?.groupKey)
        assertEquals(1, byId?.listIndex)

        val byTarget = findDatabaseItemLocation(
            duplicated,
            DatabaseFocusTarget(itemId = 5L),
            emptySet()
        )
        assertEquals("g1", byTarget?.groupKey)
        assertEquals(1, byTarget?.listIndex)
    }

    @Test
    fun `direct id hit inside a collapsed group still resolves`() {
        // characterization of existing behaviour: the direct id scan does not
        // consult collapsed state, only the fallback/index walk does.
        val location = findDatabaseItemLocation(twoGroups, 1L, setOf("g1"))

        assertEquals("g1", location?.groupKey)
        assertEquals(1, location?.listIndex)
    }
}
