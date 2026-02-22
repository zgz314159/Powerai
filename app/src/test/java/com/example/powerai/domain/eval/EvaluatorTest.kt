package com.example.powerai.domain.eval

import com.example.powerai.domain.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorTest {

    @Test
    fun testEvaluator_singleQuery_perfectMatch() {
        val dataset = mapOf("q1" to setOf("1"))

        val retrievalFn: suspend (String, Int) -> List<KnowledgeItem> = { query, limit ->
            if (query == "q1") {
                listOf(
                    KnowledgeItem(id = 1L, title = "T1", content = "content1", source = "s", pageNumber = null, category = "cat", keywords = emptyList())
                ).take(limit)
            } else {
                emptyList()
            }
        }

        val evaluator = Evaluator(retrievalFn)
        val metrics = evaluator.evaluate(dataset, limit = 5)

        // single query perfect top-1 => precision@1 == 1.0, MRR == 1.0
        assertEquals(1.0, metrics.precisionAtK[1] ?: 0.0, 1e-6)
        assertEquals(1.0, metrics.mrr, 1e-6)
    }

    @Test
    fun testAnswerEval_bleuRouge_basic() {
        val candidate = "the cat sat on the mat"
        val refs = listOf("the cat sat on the mat")
        val res = evaluateAnswer(candidate, refs)
        assertTrue("BLEU should be high for identical reference", res.bleu > 0.8)
        assertTrue("ROUGE-L should be high for identical reference", res.rougeL > 0.8)
    }
}
