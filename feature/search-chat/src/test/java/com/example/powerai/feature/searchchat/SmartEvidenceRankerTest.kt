package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEvidenceRankerTest {
    private fun item(
        id: Long,
        title: String,
        content: String,
    ) = KnowledgeItem(
        id = id,
        title = title,
        content = content,
        source = "电力安全工作规程",
        category = "安规",
        keywords = emptyList(),
    )

    @Test
    fun `extracts kv from mixed spellings`() {
        assertEquals(setOf(10.0), SmartEvidenceRanker.extractKvLevels("10KV如何验电"))
        assertEquals(setOf(10.0), SmartEvidenceRanker.extractKvLevels("10千伏验电"))
        assertEquals(setOf(221.0), SmartEvidenceRanker.extractKvLevels("221kV验电应使用"))
    }

    @Test
    fun `drops conflicting voltage when matching evidence exists`() {
        val ten = item(1, "10kV验电", "10kV验电可使用手持式验电器或验电笔")
        val high = item(2, "221kV验电", "221kV验电应使用相应电压等级接触式验电器")
        val refined =
            SmartEvidenceRanker.refine(
                question = "10KV如何验电",
                items = listOf(high, ten),
                limit = 2,
            )
        assertEquals(1, refined.size)
        assertEquals(1L, refined.single().id)
    }

    @Test
    fun `procedure questions demote tables behind prose`() {
        val prose = item(1, "10kV验电", "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。")
        val table =
            item(
                2,
                "表格#1（图1）",
                "|序号|工具名称|\n|---|---|\n|1|绝缘手套|\n|3|10kV验电器|",
            )
        val refined =
            SmartEvidenceRanker.refine(
                question = "10KV如何验电",
                items = listOf(table, prose),
                limit = 1,
            )
        assertEquals(1L, refined.single().id)
        assertTrue(refined.single().title.contains("验电"))
    }
}
