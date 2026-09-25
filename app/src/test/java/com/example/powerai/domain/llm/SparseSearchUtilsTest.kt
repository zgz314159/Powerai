package com.example.powerai.domain.llm

import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.engine.ai.SparseScorer
import com.example.powerai.engine.ai.SparseSearchUtils

class SparseSearchUtilsTest {
    private val entryA = SparseSearcher.SafetyEntry("A", "foo bar", "src1")
    private val entryB = SparseSearcher.SafetyEntry("B", "foo baz", "src2")
    private val entryC = SparseSearcher.SafetyEntry("C", "nothing", "src3")

    @Test
    fun `applyTieBreak leaves order unchanged when diff ge 1`() {
        val scores = listOf(
            SparseScorer.Score(entryA, raw = 10, final = 20.0),
            SparseScorer.Score(entryB, raw = 10, final = 18.5)
        )
        val result = SparseSearchUtils.applyTieBreak(scores)
        assertEquals(listOf(entryA, entryB), result)
    }

    @Test
    fun `applyTieBreak reorders when diff lt 1 and weight favors second`() {
        val scores = listOf(
            SparseScorer.Score(entryA, raw = 10, final = 20.0),
            SparseScorer.Score(entryB, raw = 1, final = 20.5) // higher final/raw ratio
        )
        val result = SparseSearchUtils.applyTieBreak(scores)
        // B should come first due to weight
        assertEquals(listOf(entryB, entryA), result)
    }

    @Test
    fun `permissiveFallback returns entries matching tokens or title`() {
        val entries = listOf(entryA, entryB, entryC)
        val tokens = listOf("fo")
        val fallback = SparseSearchUtils.permissiveFallback(entries, tokens, "foo")
        // A and B contain "foo" token
        assertTrue(fallback.containsAll(listOf(entryA, entryB)))
        assertFalse(fallback.contains(entryC))
    }

    @Test
    fun `permissiveFallback returns empty when nothing matches`() {
        val entries = listOf(entryA, entryB)
        val tokens = listOf("xyz")
        val fallback = SparseSearchUtils.permissiveFallback(entries, tokens, "xyz")
        assertTrue(fallback.isEmpty())
    }
}
