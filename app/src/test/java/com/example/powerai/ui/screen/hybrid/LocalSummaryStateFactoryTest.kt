package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.usecase.LocalAnswerDecision
import com.example.powerai.domain.usecase.LocalAnswerIntent
import com.example.powerai.domain.usecase.LocalEvidenceAssessment
import com.example.powerai.domain.usecase.LocalExtractiveAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSummaryStateFactoryTest {

    @Test
    fun `initial creates searching state`() {
        val state = LocalSummaryStateFactory.initial(
            query = "变压器",
            intent = LocalAnswerIntent.TOPIC_OVERVIEW
        )

        assertEquals(LocalPageState.SEARCHING, state.pageState)
        assertEquals(LocalQueryIntent.TOPIC_OVERVIEW, state.intent)
        assertTrue(state.supportNote.isNotBlank())
        assertFalse(state.canRunGemma)
    }

    @Test
    fun `fromAssessment creates streaming evidence state when gemma is running`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器",
            assessment = assessment(
                intent = LocalAnswerIntent.TOPIC_OVERVIEW,
                decision = LocalAnswerDecision.USE_GEMMA_ANSWER,
                evidenceCount = 3,
                distinctSourceCount = 2
            ),
            retrievalCount = 3,
            totalEvidenceCount = 3,
            gemmaRunning = true,
            extractiveAnswer = LocalExtractiveAnswer(
                text = "变压器资料需结合规程条文核对。",
                sourceLabel = "第30条 · 规则",
                score = 0.8f
            )
        )

        assertEquals(LocalPageState.EVIDENCE_ONLY, state.pageState)
        assertEquals("正在整理概览", state.title)
        assertTrue(state.isStreaming)
        assertTrue(state.canRunGemma)
        assertTrue(state.supportNote.contains("正在整理概览"))
        assertTrue(state.summary.contains("变压器资料需结合规程条文核对"))
    }

    @Test
    fun `fromAssessment creates insufficient state with empty-hit summary`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器在什么情况下停止运行",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.INSUFFICIENT_EVIDENCE,
                evidenceCount = 0
            ),
            retrievalCount = 0,
            totalEvidenceCount = 0,
            gemmaRunning = false
        )

        assertEquals(LocalPageState.INSUFFICIENT_EVIDENCE, state.pageState)
        assertEquals("没找到可直接回答的问题证据，换个关键词试试。", state.summary)
        assertFalse(state.canRunGemma)
    }

    @Test
    fun `fromAssessment keeps diagnostics visible`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器在什么情况下停止运行",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.INSUFFICIENT_EVIDENCE,
                evidenceCount = 1
            ),
            retrievalCount = 1,
            totalEvidenceCount = 1,
            gemmaRunning = false,
            diagnostics = "decision=INSUFFICIENT_EVIDENCE"
        )

        assertEquals("decision=INSUFFICIENT_EVIDENCE", state.diagnostics)
    }

    @Test
    fun `fromAssessment keeps structured diagnostic snapshot visible`() {
        val snapshot = LocalDiagnosticSnapshot(
            queryPlanStrategy = "definition_question",
            queryVariant = "隔离开关 的用途是什么",
            topEvidenceTitle = "158.隔离开关的主要用途是什么？",
            topEvidenceSource = "手册 · QA",
            topEvidencePreview = "答：一是检修与分段隔离。"
        )

        val state = LocalSummaryStateFactory.fromAssessment(
            query = "隔离开关有啥用",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.SHOW_EVIDENCE_ONLY,
                evidenceCount = 1
            ),
            retrievalCount = 1,
            totalEvidenceCount = 1,
            gemmaRunning = false,
            diagnosticSnapshot = snapshot
        )

        assertEquals(snapshot, state.diagnosticSnapshot)
    }

    @Test
    fun `fromAssessment creates insufficient state with shorter weak-hit summary`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器在什么情况下停止运行",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.INSUFFICIENT_EVIDENCE,
                evidenceCount = 2
            ),
            retrievalCount = 2,
            totalEvidenceCount = 2,
            gemmaRunning = false
        )

        assertEquals("已找到 2 条内容，但还不足以下结论，建议换更具体的问法。", state.summary)
    }

    @Test
    fun `applyGemmaResult creates final answer state when text is available`() {
        val previous = LocalSummaryState(
            pageState = LocalPageState.EVIDENCE_ONLY,
            query = "变压器在什么情况下停止运行",
            intent = LocalQueryIntent.FACT_QUESTION,
            title = "正在整理回答",
            supportNote = "证据可用于生成简短回答。",
            summary = "",
            evidenceCount = 2,
            totalEvidenceCount = 2,
            isStreaming = true,
            canRunGemma = true
        )

        val state = LocalSummaryStateFactory.applyGemmaResult(previous, "应立即停止运行并隔离处理。")

        assertEquals(LocalPageState.ANSWER_READY, state.pageState)
        assertEquals("回答", state.title)
        assertFalse(state.isStreaming)
        assertEquals("下面这段回答是根据当前找到的资料整理的。", state.supportNote)
    }

    @Test
    fun `evidence only fallback uses shorter title`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器在什么情况下停止运行",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.SHOW_EVIDENCE_ONLY,
                evidenceCount = 2
            ),
            retrievalCount = 2,
            totalEvidenceCount = 2,
            gemmaRunning = false
        )

        assertEquals("参考资料", state.title)
    }

    @Test
    fun `applyGemmaResult falls back to evidence summary when text is blank`() {
        val previous = LocalSummaryState(
            pageState = LocalPageState.EVIDENCE_ONLY,
            query = "变压器在什么情况下停止运行",
            intent = LocalQueryIntent.FACT_QUESTION,
            summary = "",
            evidenceCount = 2,
            totalEvidenceCount = 2,
            isStreaming = true,
            canRunGemma = true
        )

        val state = LocalSummaryStateFactory.applyGemmaResult(previous, "   ")

        assertEquals(LocalPageState.EVIDENCE_ONLY, state.pageState)
        assertTrue(state.summary.contains("已找到 2 条相关资料"))
        assertFalse(state.isStreaming)
    }

    @Test
    fun `fact evidence only note uses shorter product copy`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器在什么情况下停止运行",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.SHOW_EVIDENCE_ONLY,
                evidenceCount = 2
            ),
            retrievalCount = 2,
            totalEvidenceCount = 2,
            gemmaRunning = false
        )

        assertEquals("先把相关资料给你，你也可以先看看原文。", state.supportNote)
    }

    @Test
    fun `evidence only state shows extractive answer when available`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器更换熔丝",
            assessment = assessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.SHOW_EVIDENCE_ONLY,
                evidenceCount = 1
            ),
            retrievalCount = 1,
            totalEvidenceCount = 1,
            gemmaRunning = false,
            extractiveAnswer = LocalExtractiveAnswer(
                text = "更换变压器高压侧熔丝时，应先切断低压负荷。",
                sourceLabel = "第63条 · 铁路电力安全工作规程",
                score = 0.92f
            )
        )

        assertEquals("原文摘录", state.title)
        assertTrue(state.summary.contains("应先切断低压负荷"))
        assertEquals("先把最相关的原文给你，暂时不直接下结论。", state.supportNote)
    }

    @Test
    fun `topic overview note uses shorter product copy`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器",
            assessment = assessment(
                intent = LocalAnswerIntent.TOPIC_OVERVIEW,
                decision = LocalAnswerDecision.USE_GEMMA_ANSWER,
                evidenceCount = 3,
                distinctSourceCount = 2
            ),
            retrievalCount = 3,
            totalEvidenceCount = 3,
            gemmaRunning = true
        )

        assertTrue(state.supportNote.startsWith("找到的资料已经够用了，正在整理概览"))
    }

    @Test
    fun `procedure state uses process-specific copy`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "变压器更换流程",
            assessment = assessment(
                intent = LocalAnswerIntent.PROCEDURE,
                decision = LocalAnswerDecision.USE_GEMMA_ANSWER,
                evidenceCount = 2
            ),
            retrievalCount = 2,
            totalEvidenceCount = 2,
            gemmaRunning = true
        )

        assertEquals(LocalQueryIntent.PROCEDURE, state.intent)
        assertEquals("正在整理流程", state.title)
        assertTrue(state.supportNote.startsWith("步骤相关的内容已经够用了"))
    }

    @Test
    fun `comparison state uses comparison-specific copy`() {
        val state = LocalSummaryStateFactory.fromAssessment(
            query = "隔离开关和断路器的区别",
            assessment = assessment(
                intent = LocalAnswerIntent.COMPARISON,
                decision = LocalAnswerDecision.SHOW_EVIDENCE_ONLY,
                evidenceCount = 2
            ),
            retrievalCount = 2,
            totalEvidenceCount = 2,
            gemmaRunning = false
        )

        assertEquals(LocalQueryIntent.COMPARISON, state.intent)
        assertEquals("先把对比相关资料给你，暂时不自动总结差异。", state.supportNote)
    }

    private fun assessment(
        intent: LocalAnswerIntent,
        decision: LocalAnswerDecision,
        evidenceCount: Int,
        distinctSourceCount: Int = 0
    ): LocalEvidenceAssessment {
        return LocalEvidenceAssessment(
            intent = intent,
            decision = decision,
            evidenceCount = evidenceCount,
            promptEvidenceCount = 0,
            primaryScore = 0f,
            aggregateScore = 0f,
            strongEvidenceCount = 0,
            exactMatchCount = 0,
            distinctSourceCount = distinctSourceCount,
            reason = "test"
        )
    }
}