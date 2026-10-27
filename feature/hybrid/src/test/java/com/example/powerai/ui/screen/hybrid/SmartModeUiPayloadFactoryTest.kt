package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.model.QueryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartModeUiPayloadFactoryTest {

    @Test
    fun `fromResult maps query result into answer references and evidence`() {
        val item = KnowledgeItem(
            id = 2L,
            title = "变压器检修",
            content = "检修时应核查绝缘状态。",
            source = "manual.pdf",
            pageNumber = null,
            category = "",
            keywords = emptyList(),
            hitBlockIndex = null,
            hitBlockId = null
        )
        val result = QueryResult(
            answer = "需要核查绝缘状态。",
            references = listOf(item),
            confidence = 0.8f
        )

        val payload = SmartModeUiPayloadFactory.fromResult(result)

        assertEquals("需要核查绝缘状态。", payload.answer)
        assertEquals(1, payload.references.size)
        assertEquals(1, payload.evidence.size)
        assertEquals(item, payload.evidence.first().item)
        assertTrue(payload.evidence.first().source == "local")
    }
}