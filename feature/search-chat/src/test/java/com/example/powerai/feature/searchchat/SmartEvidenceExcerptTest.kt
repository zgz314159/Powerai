package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEvidenceExcerptTest {
    @Test
    fun `select keeps full duty sentence without char truncation`() {
        val longTail = "补充说明：" + "甲".repeat(300)
        val item =
            KnowledgeItem(
                id = 34L,
                title = "第34条",
                content =
                    "第34条 停电、检电、接地封线工作必须由二人进行,（一人操作,一人监护）。操作人员应戴绝缘手套。$longTail",
                source = "铁运安规",
                category = "安规",
                keywords = emptyList(),
            )
        val excerpt = SmartEvidenceExcerpt.select("停电怎么操作", item)
        assertTrue(excerpt.contains("一人操作"))
        assertTrue(excerpt.contains("一人监护"))
        assertFalse(excerpt.contains("甲甲甲"))
        // 不得腰斩在固定 220 字处留下半截
        assertFalse(excerpt.endsWith("甲"))
    }
}
