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

    @Test
    fun `strips trailing truncated citation title`() {
        val raw =
            """
            根据提供的资料，10kV验电可以通过手持式验电器或验电笔实现，操作人员应穿戴绝缘鞋和绝缘手套。[1]
            [1] 10k
            """.trimIndent()
        val cleaned = SmartAnswerGuard.stripMetaReasoningLead(raw)
        assertTrue(cleaned.contains("手持式验电器"))
        assertTrue(cleaned.contains("[1]"))
        assertFalse(cleaned.contains("[1] 10k"))
        assertFalse(cleaned.lines().any { it.trim() == "[1] 10k" })
    }

    @Test
    fun `strips forum meta lead`() {
        val raw =
            """
            —— 知名用户 0 个评分 2 个回答 ——
            要回答问题，需要根据提供的资料来分析。现在，请根据资料分析问题，回答问题。

            10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。[1]
            """.trimIndent()
        val cleaned = SmartAnswerGuard.stripMetaReasoningLead(raw)
        assertFalse(cleaned.contains("知名用户"))
        assertFalse(cleaned.contains("要回答问题"))
        assertTrue(cleaned.contains("手持式验电器"))
    }
}
