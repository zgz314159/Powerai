package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAnswerDecisionEngineTest {
    private fun item(
        title: String,
        content: String,
        source: String = "铁运安规",
    ) = KnowledgeItem(
        id = 1L,
        title = title,
        content = content,
        source = source,
        category = "安规",
        keywords = emptyList(),
    )

    @Test
    fun `short entity asks for clarify`() {
        val d =
            SmartAnswerDecisionEngine.decide(
                question = "变压器",
                qualifiedEvidence = emptyList(),
                confidence = SmartEvidenceConfidence.INCORRECT,
            )
        assertEquals(SmartAnswerDecisionKind.CLARIFY, d.kind)
    }

    @Test
    fun `procedure ask with duty-only article is partial not full`() {
        val article =
            item(
                "第34条",
                "第34条 停电、检电、接地封线工作必须由二人进行,（一人操作,一人监护）。操作人员应戴绝缘手套。",
            )
        val d =
            SmartAnswerDecisionEngine.decide(
                question = "停电怎么操作",
                qualifiedEvidence = listOf(article),
                confidence = SmartEvidenceConfidence.AMBIGUOUS,
            )
        assertEquals(SmartAnswerDecisionKind.PARTIAL, d.kind)
        assertTrue(d.directAnswer!!.contains("一人操作"))
        assertTrue(d.directAnswer!!.contains("一人监护"))
        assertFalse(d.directAnswer!!.contains("轮流"))
        assertTrue(d.missingHints.isNotEmpty())
    }

    @Test
    fun `matching how-to with strong prose can be full`() {
        val prose =
            item(
                "10kV验电",
                "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。",
            )
        val conf = SmartEvidenceGrader.grade("10KV如何验电", listOf(prose))
        val d =
            SmartAnswerDecisionEngine.decide(
                question = "10KV如何验电",
                qualifiedEvidence = listOf(prose),
                confidence = conf,
            )
        // 验电 how-to 有明确步骤句，允许 FULL；若判 PARTIAL 也不得胡编
        assertTrue(d.kind == SmartAnswerDecisionKind.FULL || d.kind == SmartAnswerDecisionKind.PARTIAL)
        if (d.kind == SmartAnswerDecisionKind.PARTIAL) {
            assertTrue(d.directAnswer!!.contains("验电"))
        }
    }

    @Test
    fun `near-miss gloves evidence is none not partial`() {
        val gloves =
            item(
                "绝缘手套",
                "所有高压操作必须佩戴符合等级的绝缘手套，并按规定间隔检查绝缘性能。",
            )
        val q = "变压器绝缘电阻多大"
        val conf = SmartEvidenceGrader.grade(q, listOf(gloves))
        val d =
            SmartAnswerDecisionEngine.decide(
                question = q,
                qualifiedEvidence = listOf(gloves),
                confidence = conf,
            )
        assertEquals(SmartAnswerDecisionKind.NONE, d.kind)
        assertEquals(SmartAnswerGuard.INSUFFICIENT, d.directAnswer)
    }

    @Test
    fun `outage inspection caution ask is partial not none`() {
        val article =
            item(
                "第34条",
                "停电、检电、接地封线工作必须由二人进行,（一人操作,一人监护）。检电前应先验电器具。",
            )
        val q = "停电检电要注意什么"
        val conf = SmartEvidenceGrader.grade(q, listOf(article))
        val useful = SmartAnswerDecisionEngine.hasPartialUsefulCoverage(q, listOf(article))
        val d =
            SmartAnswerDecisionEngine.decide(
                question = q,
                qualifiedEvidence = listOf(article),
                confidence = conf,
            )
        assertTrue("useful=$useful conf=$conf kind=${d.kind} reason=${d.reasonCode}", useful)
        assertTrue(
            "kind=${d.kind} reason=${d.reasonCode} conf=$conf",
            d.kind == SmartAnswerDecisionKind.PARTIAL || d.kind == SmartAnswerDecisionKind.FULL,
        )
    }

    @Test
    fun `aspect matched fact question with strong evidence is full`() {
        val item =
            item(
                "接地电阻",
                "本地条款内容关于接地电阻",
                source = "local.pdf",
            )
        val conf = SmartEvidenceGrader.grade("接地电阻", listOf(item))
        val d =
            SmartAnswerDecisionEngine.decide(
                question = "接地电阻",
                qualifiedEvidence = listOf(item),
                confidence = conf,
            )
        assertEquals(SmartAnswerDecisionKind.FULL, d.kind)
        assertEquals(null, d.directAnswer)
    }
}
