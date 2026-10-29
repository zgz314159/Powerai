package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAnswerGuardTest {
    private val evidence =
        listOf(
            KnowledgeItem(
                id = 1L,
                title = "10kV验电",
                content = "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。",
                source = "电力安全工作规程",
                category = "安规",
                keywords = emptyList(),
            ),
        )

    @Test
    fun `strips leading lone question mark`() {
        val raw = "?\n10kV验电可使用手持式验电器或验电笔。"
        val cleaned = SmartAnswerGuard.stripMetaReasoningLead(raw)
        assertFalse(cleaned.startsWith("?"))
        assertEquals("10kV验电可使用手持式验电器或验电笔。", cleaned)
    }

    @Test
    fun `coalesce falls back when answer is punctuation fragment`() {
        val bad = "，使用哪些工具，以及操作要领。"
        val fixed = SmartAnswerGuard.coalesceGroundedAnswer(bad, evidence)
        assertTrue(fixed.contains("手持式验电器") || fixed.contains("验电笔"))
        assertTrue(fixed.contains("[1]"))
        assertFalse(fixed.startsWith("，"))
    }
}
