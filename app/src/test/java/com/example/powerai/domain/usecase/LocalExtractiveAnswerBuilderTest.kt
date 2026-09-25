package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalExtractiveAnswerBuilderTest {

    @Test
    fun `extract picks rule sentence for action query`() {
        val answer = LocalExtractiveAnswerBuilder.extract(
            query = "变压器更换熔丝",
            retrievals = listOf(
                retrieval(
                    id = 63L,
                    title = "第63条 更换变压器高压侧熔丝时",
                    content = "更换变压器高压侧熔丝时，应先切断低压负荷，不准带负荷拉开100A及以上无消弧装置的低压开关。更换后应检查接触情况。",
                    score = 0.72f,
                    source = "铁运1999103号铁路电力安全工作规程"
                )
            ),
            assessment = LocalEvidenceAssessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.SHOW_EVIDENCE_ONLY,
                evidenceCount = 1,
                promptEvidenceCount = 0,
                primaryScore = 0.72f,
                aggregateScore = 0.72f,
                strongEvidenceCount = 1,
                exactMatchCount = 1,
                distinctSourceCount = 1,
                reason = "test"
            )
        )

        assertNotNull(answer)
        assertTrue(answer!!.text.contains("应先切断低压负荷"))
        assertTrue(answer.sourceLabel.contains("第63条"))
        assertTrue(answer.retrievalId == 63L)
    }

    @Test
    fun `extract returns null for weak insufficient evidence`() {
        val answer = LocalExtractiveAnswerBuilder.extract(
            query = "变压器是什么",
            retrievals = listOf(
                retrieval(
                    id = 77L,
                    title = "巡视要求",
                    content = "巡视时检查套管及引线。",
                    score = 0.22f,
                    source = "巡视手册"
                )
            ),
            assessment = LocalEvidenceAssessment(
                intent = LocalAnswerIntent.FACT_QUESTION,
                decision = LocalAnswerDecision.INSUFFICIENT_EVIDENCE,
                evidenceCount = 1,
                promptEvidenceCount = 0,
                primaryScore = 0.22f,
                aggregateScore = 0.22f,
                strongEvidenceCount = 0,
                exactMatchCount = 0,
                distinctSourceCount = 1,
                reason = "test"
            )
        )

        assertTrue(answer == null)
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