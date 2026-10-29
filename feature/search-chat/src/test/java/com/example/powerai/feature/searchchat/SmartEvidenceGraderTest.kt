package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEvidenceGraderTest {
    private fun item(
        title: String,
        content: String,
    ) = KnowledgeItem(
        id = 1L,
        title = title,
        content = content,
        source = "安规",
        category = "安规",
        keywords = emptyList(),
    )

    @Test
    fun `gloves snippet is incorrect for insulation resistance ask`() {
        val gloves =
            item(
                "绝缘手套",
                "所有高压操作必须佩戴符合等级的绝缘手套，并按规定间隔检查绝缘性能。",
            )
        val grade =
            SmartEvidenceGrader.grade(
                "变压器绝缘电阻多大",
                listOf(gloves),
            )
        assertEquals(SmartEvidenceConfidence.INCORRECT, grade)
    }

    @Test
    fun `matching 验电 prose is correct for how-to ask`() {
        val prose =
            item(
                "10kV验电",
                "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。",
            )
        val grade = SmartEvidenceGrader.grade("10KV如何验电", listOf(prose))
        assertEquals(SmartEvidenceConfidence.CORRECT, grade)
        assertTrue(SmartEvidenceGrader.answerabilityScore("10KV如何验电", prose) >= 0.55f)
    }

    @Test
    fun `transformer glove measure text is weak for bare transformer intent via score`() {
        val weak =
            item(
                "安规摘录",
                "拆除杆塔、变压器及避雷器的接地线并测量接地电阻时，应戴绝缘手套。",
            )
        // 明确问绝缘电阻时，仅「变压器+手套」应偏低
        val score = SmartEvidenceGrader.answerabilityScore("变压器绝缘电阻多大", weak)
        assertTrue(score < 0.55f)
    }

    @Test
    fun `10kV equipment maintenance matches grounding-before-work article`() {
        val hit =
            item(
                "10kV接地",
                "10kV设备检修前应先拉开开关并接地，接地后应复测确保无电后方可作业。",
            )
        val q = "10KV设备检修"
        assertTrue(SmartEvidenceRanker.isRelevantToQuestion(q, hit))
        val score = SmartEvidenceGrader.answerabilityScore(q, hit)
        assertTrue("score=$score", score >= 0.55f)
        assertEquals(SmartEvidenceConfidence.CORRECT, SmartEvidenceGrader.grade(q, listOf(hit)))
    }
}
