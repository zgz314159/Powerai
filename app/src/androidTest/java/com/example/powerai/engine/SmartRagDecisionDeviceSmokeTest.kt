package com.example.powerai.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.feature.searchchat.SmartAnswerDecisionEngine
import com.example.powerai.feature.searchchat.SmartAnswerDecisionKind
import com.example.powerai.feature.searchchat.SmartAnswerGuard
import com.example.powerai.feature.searchchat.SmartEvidenceConfidence
import com.example.powerai.feature.searchchat.SmartEvidenceGrader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P5 真机：截图回归决策/守卫路径（不依赖模型采样）。
 * 验证 PARTIAL 保留职责原文，坏生成「轮流」被回退。
 */
@RunWith(AndroidJUnit4::class)
class SmartRagDecisionDeviceSmokeTest {
    private val duty =
        KnowledgeItem(
            id = 34L,
            title = "第34条",
            content =
                "第34条 停电、检电、接地封线工作必须由二人进行,（一人操作,一人监护）。操作人员应戴绝缘手套。",
            source = "铁运安规",
            category = "安规",
            keywords = emptyList(),
        )

    @Test
    fun screenshot_outage_ask_is_partial_with_duty_facts() {
        val q = "停电怎么操作"
        val conf = SmartEvidenceGrader.grade(q, listOf(duty))
        val d =
            SmartAnswerDecisionEngine.decide(
                question = q,
                qualifiedEvidence = listOf(duty),
                confidence = conf,
            )
        assertEquals(SmartAnswerDecisionKind.PARTIAL, d.kind)
        assertTrue(d.directAnswer!!.contains("一人操作"))
        assertTrue(d.directAnswer!!.contains("一人监护"))
        assertFalse(d.directAnswer!!.contains("轮流"))
    }

    @Test
    fun injected_duty_rewrite_falls_back_to_excerpt() {
        val bad = "根据资料，停电时两人轮流操作即可。"
        val fixed =
            SmartAnswerGuard.coalesceGroundedAnswer(
                raw = bad,
                references = listOf(duty),
                question = "停电怎么操作",
            )
        assertTrue(fixed.contains("一人操作"))
        assertFalse(fixed.contains("轮流"))
        assertTrue(
            SmartAnswerGuard.shouldReplaceVisibleAnswer(
                resolved = fixed,
                currentVisible = bad + "补丁".repeat(40),
                references = listOf(duty),
            ),
        )
    }

    @Test
    fun short_entity_clarifies() {
        val d =
            SmartAnswerDecisionEngine.decide(
                question = "变压器",
                qualifiedEvidence = emptyList(),
                confidence = SmartEvidenceConfidence.INCORRECT,
            )
        assertEquals(SmartAnswerDecisionKind.CLARIFY, d.kind)
    }
}
