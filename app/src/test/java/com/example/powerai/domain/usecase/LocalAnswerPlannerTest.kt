package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAnswerPlannerTest {

    @Test
    fun `classifyIntent treats question form as fact question`() {
        assertEquals(
            LocalAnswerIntent.FACT_QUESTION,
            LocalAnswerPlanner.classifyIntent("变压器在什么情况下停止运行？")
        )
    }

    @Test
    fun `classifyIntent treats short stop-run phrase as fact question`() {
        assertEquals(
            LocalAnswerIntent.FACT_QUESTION,
            LocalAnswerPlanner.classifyIntent("变压器停止运行")
        )
    }

    @Test
    fun `classifyIntent treats noun topic as overview`() {
        assertEquals(
            LocalAnswerIntent.TOPIC_OVERVIEW,
            LocalAnswerPlanner.classifyIntent("变压器")
        )
    }

    @Test
    fun `classifyIntent treats procedure query as procedure`() {
        assertEquals(
            LocalAnswerIntent.PROCEDURE,
            LocalAnswerPlanner.classifyIntent("变压器更换流程")
        )
    }

    @Test
    fun `classifyIntent treats comparison query as comparison`() {
        assertEquals(
            LocalAnswerIntent.COMPARISON,
            LocalAnswerPlanner.classifyIntent("隔离开关和断路器的区别")
        )
    }

    @Test
    fun `assess uses gemma for procedure query with strong evidence`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器更换流程",
            retrievals = listOf(
                retrieval(1L, "更换步骤", "步骤一停电验电，步骤二拆除连接，步骤三恢复送电。", 0.36f, "rule-a.pdf"),
                retrieval(2L, "作业流程", "更换前应办理停电手续并确认隔离完成。", 0.28f, "rule-a.pdf")
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
    }

    @Test
    fun `assess uses gemma for comparison query with multi-source evidence`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "隔离开关和断路器的区别",
            retrievals = listOf(
                retrieval(1L, "隔离开关功能", "隔离开关用于形成明显断开点，不具备开断负荷电流能力。", 0.24f, "rule-a.pdf"),
                retrieval(2L, "断路器功能", "断路器具备开断负荷电流和故障电流能力。", 0.23f, "rule-b.pdf"),
                retrieval(3L, "使用差异", "两者在保护和操作能力上存在明显差异。", 0.20f, "rule-c.pdf")
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
    }

    @Test
    fun `assess returns insufficient evidence when retrievals are empty`() {
        val assessment = LocalAnswerPlanner.assess("变压器在什么情况下停止运行", emptyList())

        assertEquals(LocalAnswerDecision.INSUFFICIENT_EVIDENCE, assessment.decision)
        assertEquals("no_evidence", assessment.reason)
    }

    @Test
    fun `assess uses gemma for fact question with exact match`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器在什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 1L,
                    title = "⭐变压器停运条件",
                    content = "当设备故障危及安全时应立即停止运行，并执行隔离措施。",
                    confidence = 0.42f,
                    source = "rule-a.pdf",
                    exact = true
                )
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
        assertTrue(assessment.promptEvidenceCount >= 1)
    }

    @Test
    fun `assess keeps weak fact hits in evidence only`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器在什么情况下停止运行",
            retrievals = listOf(
                retrieval(1L, "背景说明", "简短说明", 0.18f, "rule-a.pdf"),
                retrieval(2L, "相关条文", "再一条较短说明", 0.17f, "rule-a.pdf")
            )
        )

        assertEquals(LocalAnswerDecision.SHOW_EVIDENCE_ONLY, assessment.decision)
    }

    @Test
    fun `assess uses gemma for topic overview with multi-source coverage`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器",
            retrievals = listOf(
                retrieval(1L, "运行要求", "变压器运行管理要求包括巡视、维护、停运检查等。", 0.26f, "rule-a.pdf"),
                retrieval(2L, "检修要点", "检修阶段关注绝缘、温升和异常状态记录。", 0.25f, "rule-b.pdf")
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
        assertTrue(assessment.distinctSourceCount >= 2)
    }

    @Test
    fun `representative fact query keeps answer generation enabled`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器在什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 1L,
                    title = "⭐变压器停运条件",
                    content = "当设备故障危及安全、异常运行无法控制或检修要求停运时，应立即停止运行。",
                    confidence = 0.40f,
                    source = "rule-a.pdf",
                    exact = true
                ),
                retrieval(
                    id = 2L,
                    title = "停运处置",
                    content = "停运后应执行隔离、验电和记录。",
                    confidence = 0.27f,
                    source = "rule-a.pdf"
                )
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
    }

    @Test
    fun `assess treats retrieved stop-run clause as strong evidence without explicit fts bonus`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器在什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 30L,
                    title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                    content = "运行变压器有下列情况之一时，应立即停止运行：一 变压器内部音响很大 二 在正常冷却条件下温度不断上升 三 干式变压器绕组有放电声并有异味 四 高低压接线套管严重放电。",
                    confidence = 0.36f,
                    source = "rule-transformer.pdf"
                )
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
        assertTrue(assessment.exactMatchCount > 0)
    }

    @Test
    fun `representative topic query keeps overview generation enabled`() {
        val assessment = LocalAnswerPlanner.assess(
            query = "变压器",
            retrievals = listOf(
                retrieval(1L, "运行要求", "变压器运行管理包括巡视、维护、停运检查。", 0.26f, "rule-a.pdf"),
                retrieval(2L, "检修要点", "检修阶段关注绝缘、温升和异常记录。", 0.25f, "rule-b.pdf"),
                retrieval(3L, "附属设备", "附属保护和冷却装置也需同步检查。", 0.21f, "rule-c.pdf")
            )
        )

        assertEquals(LocalAnswerDecision.USE_GEMMA_ANSWER, assessment.decision)
        assertTrue(assessment.promptEvidenceCount >= 2)
    }

    private fun retrieval(
        id: Long,
        title: String,
        content: String,
        confidence: Float,
        source: String,
        exact: Boolean = false
    ): RetrievalResult {
        return RetrievalResult(
            id = id,
            score = confidence,
            confidence = confidence,
            source = source,
            metadata = mapOf("source" to source, "title" to title),
            item = KnowledgeItem(
                id = id,
                title = title,
                content = content,
                source = source,
                pageNumber = null,
                category = "",
                keywords = emptyList(),
                hitBlockIndex = null,
                hitBlockId = null
            ),
            debug = if (exact) mapOf("fts_bonus_applied" to true) else null
        )
    }
}