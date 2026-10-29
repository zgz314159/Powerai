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

    @Test
    fun `drops gloves evidence for transformer insulation resistance ask`() {
        val gloves =
            item(
                1,
                "绝缘手套",
                "所有高压操作必须佩戴符合等级的绝缘手套，并按规定间隔检查绝缘性能。",
            )
        val refined =
            SmartEvidenceRanker.refine(
                question = "变压器绝缘电阻多大",
                items = listOf(gloves),
                limit = 2,
            )
        assertTrue(refined.isEmpty())
    }

    @Test
    fun `keeps matching 验电 evidence for how-to ask`() {
        val ten = item(1, "10kV验电", "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。")
        val refined =
            SmartEvidenceRanker.refine(
                question = "10KV如何验电",
                items = listOf(ten),
                limit = 1,
            )
        assertEquals(1, refined.size)
        assertEquals(1L, refined.single().id)
    }

    @Test
    fun `keeps 10kV grounding article for equipment maintenance ask`() {
        val hit =
            item(
                1,
                "10kV接地",
                "10kV设备检修前应先拉开开关并接地，接地后应复测确保无电后方可作业。",
            )
        val noise =
            item(
                2,
                "绝缘手套",
                "所有高压操作必须佩戴符合等级的绝缘手套，并按规定间隔检查绝缘性能。",
            )
        val refined =
            SmartEvidenceRanker.refine(
                question = "10KV设备检修",
                items = listOf(noise, hit),
                limit = 2,
            )
        assertTrue(refined.any { it.id == 1L })
        assertTrue(refined.none { it.id == 2L })
    }
}
