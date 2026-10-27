package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.domain.usecase.LocalAnswerDecision
import com.example.powerai.domain.usecase.LocalAnswerIntent
import com.example.powerai.domain.usecase.LocalDiscardedCandidate
import com.example.powerai.domain.usecase.LocalDiscardReason
import com.example.powerai.domain.usecase.LocalDiscardThresholdSnapshot
import com.example.powerai.domain.usecase.LocalEvidenceAssessment
import com.example.powerai.domain.usecase.LocalExtractiveAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModeUiPayloadFactoryTest {

    @Test
    fun `fromOutcome maps local mode result into ui payload`() {
        val item = KnowledgeItem(
            id = 1L,
            title = "变压器停运条件",
            content = "严重故障时应停运。",
            source = "rule.pdf",
            pageNumber = null,
            category = "",
            keywords = emptyList(),
            hitBlockIndex = null,
            hitBlockId = null
        )
        val retrieval = RetrievalResult(
            id = 1L,
            score = 0.7f,
            confidence = 0.7f,
            source = "local",
            debug = mapOf(
                "query_plan_strategy" to "condition_question",
                "query_variant" to "变压器 在什么情况下 停止运行",
                "query_variant_hit_count" to 3,
                "context_label" to "第30条 · 规则",
                "rerank_score" to "0.82",
                "contextual_signal" to "0.28",
                "anchor_hits" to 2,
                "qa_style" to false
            ),
            item = item
        )
        val gemmaJob = kotlinx.coroutines.Job()
        val outcome = HybridQueryUseCase.LocalModeResult(
            query = HybridQueryUseCase.LocalQueryResult(
                retrievals = listOf(retrieval),
                items = listOf(item),
                totalCandidateCount = 4,
                refinedCandidateCount = 2,
                discardedCandidates = listOf(
                    LocalDiscardedCandidate(
                        title = "巡视要求",
                        source = "附录A · rule.pdf",
                        reason = LocalDiscardReason.NOISY_MARKERS,
                        reasonDetailCode = "matched_noisy_terms",
                        reasonDetail = "附件, 巡视",
                        thresholdSnapshot = LocalDiscardThresholdSnapshot(
                            combinedSignal = "0.32",
                            combinedThreshold = "0.34",
                            anchorHits = 0,
                            anchorRequired = false,
                            primaryFailedGate = "combined_signal",
                            failedGateCount = 2,
                            gates = listOf(
                                com.example.powerai.domain.usecase.LocalDiscardRuleGateSnapshot(
                                    code = "combined_signal",
                                    passed = false,
                                    actual = "0.32",
                                    expected = "0.34"
                                ),
                                com.example.powerai.domain.usecase.LocalDiscardRuleGateSnapshot(
                                    code = "noise_filter",
                                    passed = false,
                                    actual = "matched",
                                    expected = "clean"
                                )
                            )
                        ),
                        tokenCoverage = "0.22",
                        contextualSignal = "0.10"
                    )
                ),
                extractiveAnswer = LocalExtractiveAnswer(
                    text = "严重故障时应停运。",
                    sourceLabel = "变压器停运条件 · rule.pdf",
                    score = 0.88f,
                    retrievalId = 1L
                )
            ),
            assessment = LocalEvidenceAssessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.USE_GEMMA_ANSWER,
                evidenceCount = 1,
                promptEvidenceCount = 1,
                primaryScore = 0.7f,
                aggregateScore = 0.7f,
                strongEvidenceCount = 1,
                exactMatchCount = 1,
                distinctSourceCount = 1,
                reason = "test"
            ),
            gemmaJob = gemmaJob
        )

        val payload = LocalModeUiPayloadFactory.fromOutcome(
            query = "变压器在什么情况下停止运行",
            outcome = outcome
        )

        assertEquals(1, payload.references.size)
        assertEquals(1, payload.evidence.size)
        assertEquals(4, payload.summaryState.totalEvidenceCount)
        assertEquals(LocalPageState.EVIDENCE_ONLY, payload.summaryState.pageState)
        assertTrue(payload.summaryState.isStreaming)
        assertTrue(payload.summaryState.summary.contains("严重故障时应停运"))
        assertEquals("严重故障时应停运。", payload.references.first().highlightHint)
        assertEquals("变压器停运条件 · rule.pdf", payload.references.first().contextLabel)
        assertTrue(payload.summaryState.diagnosticSnapshot.queryPlanPreview.isNotBlank())
        assertTrue(
            payload.summaryState.diagnosticSnapshot.queryPlanPreview.contains("停止运行") ||
                payload.summaryState.diagnosticSnapshot.queryPlanPreview.contains("停运")
        )
        assertEquals("condition_question", payload.summaryState.diagnosticSnapshot.queryPlanStrategy)
        assertEquals("变压器 在什么情况下 停止运行", payload.summaryState.diagnosticSnapshot.queryVariant)
        assertEquals("3", payload.summaryState.diagnosticSnapshot.queryVariantHitCount)
        assertEquals("变压器停运条件", payload.summaryState.diagnosticSnapshot.topEvidenceTitle)
        assertTrue(payload.summaryState.diagnosticSnapshot.topEvidencePreview.contains("严重故障时应停运"))
        assertEquals(1, payload.summaryState.diagnosticSnapshot.topHits.first().rank)
        assertEquals("变压器停运条件", payload.summaryState.diagnosticSnapshot.topHits.first().title)
        assertEquals("0.70", payload.summaryState.diagnosticSnapshot.topHits.first().score)
        assertEquals("0.82", payload.summaryState.diagnosticSnapshot.topHits.first().rerankScore)
        assertEquals("0.28", payload.summaryState.diagnosticSnapshot.topHits.first().contextualSignal)
        assertEquals("2", payload.summaryState.diagnosticSnapshot.topHits.first().anchorHits)
        assertEquals("false", payload.summaryState.diagnosticSnapshot.topHits.first().qaStyle)
        assertEquals(1, payload.summaryState.diagnosticSnapshot.discardedHits.size)
        assertEquals("巡视要求", payload.summaryState.diagnosticSnapshot.discardedHits.first().title)
        assertEquals("noisy_markers", payload.summaryState.diagnosticSnapshot.discardedHits.first().reasonCode)
        assertEquals("噪声标记偏强", payload.summaryState.diagnosticSnapshot.discardedHits.first().reason)
        assertEquals("matched_noisy_terms", payload.summaryState.diagnosticSnapshot.discardedHits.first().reasonDetailCode)
        assertEquals("附件, 巡视", payload.summaryState.diagnosticSnapshot.discardedHits.first().reasonDetail)
        assertEquals("0.32", payload.summaryState.diagnosticSnapshot.discardedHits.first().combinedSignal)
        assertEquals("0.34", payload.summaryState.diagnosticSnapshot.discardedHits.first().combinedThreshold)
        assertEquals("0", payload.summaryState.diagnosticSnapshot.discardedHits.first().anchorHits)
        assertEquals("false", payload.summaryState.diagnosticSnapshot.discardedHits.first().anchorRequired)
        assertEquals("combined_signal", payload.summaryState.diagnosticSnapshot.discardedHits.first().primaryFailedGate)
        assertEquals("2", payload.summaryState.diagnosticSnapshot.discardedHits.first().failedGateCount)
        assertEquals(2, payload.summaryState.diagnosticSnapshot.discardedHits.first().gateSummaries.size)
        assertEquals("combined_signal", payload.summaryState.diagnosticSnapshot.discardedHits.first().gateSummaries.first().code)
        assertEquals("false", payload.summaryState.diagnosticSnapshot.discardedHits.first().gateSummaries.first().passed)
        assertTrue(payload.summaryState.diagnostics.contains("detail_code=matched_noisy_terms detail=附件, 巡视"))
        assertTrue(payload.summaryState.diagnostics.contains("combined=0.32/0.34 anchor=0/0 failed=综合信号(combined_signal)+2"))
        assertTrue(payload.summaryState.diagnostics.contains("gates=综合信号(combined_signal):fail(0.32->0.34)|噪声过滤(noise_filter):fail(matched->clean)"))
        assertFalse(payload.summaryState.diagnostics.contains("failed=combined_signal+2"))
        assertFalse(payload.summaryState.diagnostics.contains("gates=combined_signal:fail(0.32->0.34)|noise_filter:fail(matched->clean)"))
        assertSame(gemmaJob, payload.gemmaJob)
    }

    @Test
    fun `fromOutcome strips opaque hash source labels from ui payload`() {
        val hashSource = "8f14e45fceea167a5a36dedd4bea2543f2d9d5bb1c4f0f8bb6f2b3f5a1c9d001"
        val item = KnowledgeItem(
            id = 9L,
            title = "第30条 变压器停运条件",
            content = "严重故障时应停运。",
            source = hashSource,
            pageNumber = 3,
            category = "",
            keywords = emptyList(),
            hitBlockIndex = null,
            hitBlockId = null
        )
        val outcome = HybridQueryUseCase.LocalModeResult(
            query = HybridQueryUseCase.LocalQueryResult(
                retrievals = listOf(
                    RetrievalResult(
                        id = 9L,
                        score = 0.7f,
                        confidence = 0.7f,
                        source = "local",
                        debug = mapOf("context_label" to "第30条 · $hashSource"),
                        item = item
                    )
                ),
                items = listOf(item),
                totalCandidateCount = 1,
                refinedCandidateCount = 1,
                discardedCandidates = emptyList(),
                extractiveAnswer = LocalExtractiveAnswer(
                    text = "严重故障时应停运。",
                    sourceLabel = "第30条 · $hashSource",
                    score = 0.9f,
                    retrievalId = 9L
                )
            ),
            assessment = LocalEvidenceAssessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.USE_GEMMA_ANSWER,
                evidenceCount = 1,
                promptEvidenceCount = 1,
                primaryScore = 0.7f,
                aggregateScore = 0.7f,
                strongEvidenceCount = 1,
                exactMatchCount = 1,
                distinctSourceCount = 1,
                reason = "test"
            ),
            gemmaJob = null
        )

        val payload = LocalModeUiPayloadFactory.fromOutcome(
            query = "变压器在什么情况下停止运行",
            outcome = outcome
        )

        assertEquals("第30条", payload.references.first().contextLabel)
    }

    @Test
    fun `fromOutcome promotes answer aligned evidence to the front`() {
        val item1 = KnowledgeItem(
            id = 1L,
            title = "电抗器巡视要求",
            content = "巡视时应检查电抗器外观。",
            source = "rule-a.pdf",
            pageNumber = 14,
            category = "",
            keywords = emptyList(),
            hitBlockIndex = null,
            hitBlockId = null
        )
        val item2 = KnowledgeItem(
            id = 2L,
            title = "第37条 高速铁路电力设备巡视",
            content = "如需开启具有远动上传信号的设备房屋、变压器室、箱变、电抗器等门锁，需向铁路局供电调度口头申请。",
            source = "rule-b.pdf",
            pageNumber = 6,
            category = "",
            keywords = emptyList(),
            hitBlockIndex = 0,
            hitBlockId = "b0"
        )
        val retrieval1 = RetrievalResult(
            id = 1L,
            score = 0.92f,
            confidence = 0.92f,
            source = "local",
            item = item1
        )
        val retrieval2 = RetrievalResult(
            id = 2L,
            score = 0.84f,
            confidence = 0.84f,
            source = "local",
            item = item2
        )
        val outcome = HybridQueryUseCase.LocalModeResult(
            query = HybridQueryUseCase.LocalQueryResult(
                retrievals = listOf(retrieval1, retrieval2),
                items = listOf(item1, item2),
                totalCandidateCount = 2,
                refinedCandidateCount = 2,
                discardedCandidates = emptyList(),
                extractiveAnswer = LocalExtractiveAnswer(
                    text = "第三十七条 高速铁路电力设备巡视时，如需开启具有远动上传信号的设备房屋、变压器室、箱变、电抗器等门锁，需向铁路局供电调度口头申请。",
                    sourceLabel = "第37条 · rule-b.pdf",
                    score = 0.91f,
                    retrievalId = 2L
                )
            ),
            assessment = LocalEvidenceAssessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.USE_GEMMA_ANSWER,
                evidenceCount = 2,
                promptEvidenceCount = 2,
                primaryScore = 0.92f,
                aggregateScore = 0.88f,
                strongEvidenceCount = 2,
                exactMatchCount = 1,
                distinctSourceCount = 2,
                reason = "test"
            ),
            gemmaJob = null
        )

        val payload = LocalModeUiPayloadFactory.fromOutcome(
            query = "电抗器门锁如何开启",
            outcome = outcome
        )

        assertEquals(2L, payload.references.first().id)
        assertTrue(payload.references.first().highlightHint?.contains("第三十七条") == true)
        assertEquals(2L, payload.evidence.first().item?.id)
    }
}