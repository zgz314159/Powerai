package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSemanticClaimGuardTest {
    private val dutyEvidence =
        listOf(
            KnowledgeItem(
                id = 34L,
                title = "第34条",
                content = "第34条 停电、检电、接地封线工作必须由二人进行,（一人操作,一人监护）。操作人员应戴绝缘手套。",
                source = "铁运安规",
                category = "安规",
                keywords = emptyList(),
            ),
        )

    @Test
    fun `detects duty rewrite to轮流`() {
        val bad = "停电作业可由两人轮流操作，互相监护即可。"
        assertEquals("DUTY_REWRITE", SmartSemanticClaimGuard.findRewriteViolation(bad, dutyEvidence))
        val fixed = SmartAnswerGuard.coalesceGroundedAnswer(bad, dutyEvidence, "停电怎么操作")
        assertTrue(fixed.contains("一人操作"))
        assertTrue(fixed.contains("一人监护"))
        assertFalse(fixed.contains("轮流"))
    }

    @Test
    fun `rewrite visible answer is always replaced`() {
        val hallucination = "根据资料，停电时两人轮流操作比较安全。" + "补".repeat(80)
        val resolved = SmartAnswerGuard.coalesceGroundedAnswer(hallucination, dutyEvidence, "停电怎么操作")
        assertTrue(
            SmartAnswerGuard.shouldReplaceVisibleAnswer(
                resolved = resolved,
                currentVisible = hallucination,
                references = dutyEvidence,
            ),
        )
        assertTrue(resolved.length < hallucination.length)
    }
}
