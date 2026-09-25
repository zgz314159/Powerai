package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEvidenceRefinerTest {

    @Test
    fun `refine keeps stop-run evidence and filters transformer-only noise`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "变压器什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 30L,
                    title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                    content = "运行变压器有下列情况之一时，应立即停止运行：（一）变压器内部音响很大。",
                    score = 0.52f,
                    source = "rule-30"
                ),
                retrieval(
                    id = 63L,
                    title = "第63条 更换变压器高压侧熔丝时",
                    content = "更换变压器高压侧熔丝时，应先切断低压负荷。",
                    score = 0.48f,
                    source = "rule-63"
                ),
                retrieval(
                    id = 44L,
                    title = "第四十四条 干式变压器维护原则",
                    content = "干式变压器等主要设备应实行寿命管理。",
                    score = 0.44f,
                    source = "rule-44"
                )
            ),
            displayLimit = 3
        )

        assertEquals(1, refined.retrievals.size)
        assertEquals(30L, refined.retrievals.first().id)
        assertEquals(3, refined.totalCandidateCount)
        assertTrue(refined.discardedCandidates.isNotEmpty())
        assertTrue(refined.discardedCandidates.any { it.reason == LocalDiscardReason.MISSING_ANCHOR || it.reason == LocalDiscardReason.LOW_RELEVANCE })
    }

    @Test
    fun `refine deduplicates same article from rule and handbook`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "变压器什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 30L,
                    title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                    content = "运行变压器有下列情况之一时，应立即停止运行。",
                    score = 0.51f,
                    source = "rule-30"
                ),
                retrieval(
                    id = 88L,
                    title = "88.运行变压器发生哪些情况应立即停止运行? （《高速铁路电力管理规则》第30条）",
                    content = "（一）变压器内部音响很大。",
                    score = 0.49f,
                    source = "qa-88"
                )
            ),
            displayLimit = 3
        )

        assertEquals(1, refined.retrievals.size)
        assertTrue(refined.refinedCandidateCount >= 1)
    }

    @Test
    fun `refine keeps fuse replacement rule for action query`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "变压器更换熔丝",
            retrievals = listOf(
                retrieval(
                    id = 63L,
                    title = "第63条 更换变压器高压侧熔丝时",
                    content = "更换变压器高压侧熔丝时，应先切断低压负荷，不准带负荷拉开100A及以上无消弧装置的低压开关。",
                    score = 0.40f,
                    source = "rule-63"
                ),
                retrieval(
                    id = 30L,
                    title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                    content = "运行变压器有下列情况之一时，应立即停止运行。",
                    score = 0.52f,
                    source = "rule-30"
                ),
                retrieval(
                    id = 77L,
                    title = "变压器巡视要求",
                    content = "巡视时检查套管及引线。",
                    score = 0.39f,
                    source = "rule-77"
                )
            ),
            displayLimit = 3
        )

        assertEquals(1, refined.retrievals.size)
        assertEquals(63L, refined.retrievals.first().id)
        assertTrue(refined.retrievals.all { it.id != 30L })
        assertTrue(refined.retrievals.first().debug?.containsKey("context_label") == true)
    }

    @Test
    fun `refine keeps handbook qa for purpose query without exact wording`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "隔离开关用途",
            retrievals = listOf(
                retrieval(
                    id = 158L,
                    title = "158 隔离开关的主要用途是什么",
                    content = "答：一是检修与分段隔离，二是倒换母线，三是分、合空载电路。",
                    score = 0.41f,
                    source = "铁路电力线路工岗位学标考标必知必会手册"
                ),
                retrieval(
                    id = 201L,
                    title = "第60条 停电操作必须按照断路器、负荷侧隔离开关、电源侧隔离开关顺序操作",
                    content = "送电操作顺序与此相反。",
                    score = 0.43f,
                    source = "rule-60"
                )
            ),
            displayLimit = 3
        )

        assertEquals(1, refined.retrievals.size)
        assertEquals(158L, refined.retrievals.first().id)
    }

    @Test
    fun `refine prioritizes subject-matched rule evidence for capacitor purpose query`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "电容用途",
            retrievals = listOf(
                retrieval(
                    id = 73L,
                    title = "第73条 大电容设备或电容器耐压试验前后应充分接地、短路放电",
                    content = "对大电容设备或电容器进行耐压试验前后，应充分接地、短路放电，防止残余电荷伤人。",
                    score = 0.42f,
                    source = "高速铁路电力管理规则"
                ),
                retrieval(
                    id = 107L,
                    title = "107.普通拉线的用途是什么？",
                    content = "答：用于保持电杆稳定。",
                    score = 0.46f,
                    source = "铁路电力线路工岗位学标考标必知必会手册"
                ),
                retrieval(
                    id = 166L,
                    title = "166.电缆线路停电后为何短时间内还有电？用什么方法消除？",
                    content = "答：由于线路存在电容效应，停电后仍可能短时间带电，应通过接地放电消除。",
                    score = 0.45f,
                    source = "铁路电力线路工岗位学标考标必知必会手册"
                )
            ),
            displayLimit = 3
        )

        assertEquals(1, refined.retrievals.size)
        assertEquals(73L, refined.retrievals.first().id)
        assertTrue(refined.discardedCandidates.any { it.title.contains("普通拉线") })
    }

    @Test
    fun `refine marks appendix style candidate as noisy markers`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "变压器在什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 30L,
                    title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                    content = "运行变压器有下列情况之一时，应立即停止运行。",
                    score = 0.61f,
                    source = "rule-30"
                ),
                retrieval(
                    id = 901L,
                    title = "附件A 巡视配备标准",
                    content = "附件 序号 备注 巡视 配备标准。",
                    score = 0.58f,
                    source = "appendix-A"
                )
            ),
            displayLimit = 3
        )

        assertTrue(refined.discardedCandidates.isNotEmpty())
        assertEquals(LocalDiscardReason.NOISY_MARKERS, refined.discardedCandidates.first().reason)
        assertEquals("matched_noisy_terms", refined.discardedCandidates.first().reasonDetailCode)
        assertTrue(refined.discardedCandidates.first().reasonDetail.contains("附件") || refined.discardedCandidates.first().reasonDetail.contains("巡视"))
        assertEquals("0.50", refined.discardedCandidates.first().thresholdSnapshot.combinedThreshold)
        assertEquals(0, refined.discardedCandidates.first().thresholdSnapshot.anchorHits)
        assertEquals("combined_signal", refined.discardedCandidates.first().thresholdSnapshot.primaryFailedGate)
        assertEquals(3, refined.discardedCandidates.first().thresholdSnapshot.failedGateCount)
        assertTrue(refined.discardedCandidates.first().thresholdSnapshot.gates.any { it.code == "anchor_gate" && !it.passed })
        assertTrue(refined.discardedCandidates.first().thresholdSnapshot.gates.any { it.code == "noise_filter" && !it.passed })
        assertTrue(refined.discardedCandidates.first().thresholdSnapshot.gates.any { it.code == "combined_signal" && !it.passed })
    }

    @Test
    fun `refine records missing anchor details for condition-like off-topic candidate`() {
        val refined = LocalEvidenceRefiner.refine(
            query = "变压器什么情况下停止运行",
            retrievals = listOf(
                retrieval(
                    id = 30L,
                    title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                    content = "运行变压器有下列情况之一时，应立即停止运行。",
                    score = 0.65f,
                    source = "rule-30"
                ),
                retrieval(
                    id = 501L,
                    title = "变压器异常情况分析",
                    content = "运行变压器发生异常情况时，应及时上报并分析原因。",
                    score = 0.56f,
                    source = "rule-501"
                )
            ),
            displayLimit = 3
        )

        val discarded = refined.discardedCandidates.firstOrNull { it.reason == LocalDiscardReason.MISSING_ANCHOR }
        assertTrue(discarded != null)
        assertEquals("expected_anchor_missing", discarded?.reasonDetailCode)
        assertTrue(discarded?.reasonDetail?.contains("停止运行") == true || discarded?.reasonDetail?.contains("停运") == true)
        assertEquals("0.50", discarded?.thresholdSnapshot?.combinedThreshold)
        assertEquals(true, discarded?.thresholdSnapshot?.anchorRequired)
        assertEquals("anchor_gate", discarded?.thresholdSnapshot?.primaryFailedGate)
        assertTrue(discarded?.thresholdSnapshot?.gates?.any { it.code == "anchor_gate" && !it.passed } == true)
    }

    private fun retrieval(
        id: Long,
        title: String,
        content: String,
        score: Float,
        source: String
    ): RetrievalResult {
        return RetrievalResult(
            id = id,
            score = score,
            confidence = score,
            source = "fts",
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
            )
        )
    }
}