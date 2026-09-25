package com.example.powerai.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.example.powerai.core.model.util.BlocksTextExtractor

class BlocksTextExtractorTest {

    @Test
    fun `findBestMatchingBlockTarget prefers answer block over neighboring article context`() {
        val blocksJson = """
            [
              {"id":"b1","type":"text","text":"第63条 更换变压器高压侧熔丝时 · 铁路电力安全工作规程"},
              {"id":"b2","type":"text","text":"更换变压器高压侧熔丝时，应先切断低压负荷。"},
              {"id":"b3","type":"text","text":"更换后应检查接触情况。"}
            ]
        """.trimIndent()

        val target = BlocksTextExtractor.findBestMatchingBlockTarget(
            blocksJson = blocksJson,
            highlight = "更换变压器高压侧熔丝时，应先切断低压负荷。",
            fallbackContext = "第63条 铁路电力安全工作规程"
        )

        assertNotNull(target)
        assertEquals(1, target!!.index)
        assertEquals("b2", target.id)
    }
}