package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnowledgeDetailTargetResolverTest {

    @Test
    fun `knowledge detail target prefers item highlight hint`() {
        val item = KnowledgeItem(
            id = 42L,
            title = "变压器",
            content = "内容",
            source = "规则",
            pageNumber = 3,
            category = "qa",
            keywords = emptyList(),
            highlightHint = "命中句",
            hitBlockIndex = 7,
            hitBlockId = "blk-7"
        )

        val target = knowledgeDetailTargetOrNull(item, fallbackHighlight = "查询词")

        assertEquals(42L, target?.id)
        assertEquals(7, target?.blockIndex)
        assertEquals("blk-7", target?.blockId)
        assertEquals("命中句", target?.detailHighlight)
    }

    @Test
    fun `citation target falls back to query highlight and rejects invalid citation`() {
        val item = KnowledgeItem(
            id = 9L,
            title = "断路器",
            content = "内容",
            source = "题库",
            pageNumber = null,
            category = "qa",
            keywords = emptyList(),
            highlightHint = null,
            hitBlockIndex = null,
            hitBlockId = null
        )

        val target = citationDetailTargetOrNull(
            citationNumber = 1,
            evidenceItems = listOf(item),
            fallbackHighlight = "断路器区别"
        )

        assertEquals(9L, target?.id)
        assertEquals("断路器区别", target?.detailHighlight)
        assertNull(citationDetailTargetOrNull(2, listOf(item), "断路器区别"))
        assertNull(citationDetailTargetOrNull(0, listOf(item), "断路器区别"))
    }
}