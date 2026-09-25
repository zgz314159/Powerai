package com.example.powerai.domain.llm

import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.engine.ai.SparseScorer

class SparseScorerTest {
    private val entryA = SparseSearcher.SafetyEntry("A","foo bar","r1")
    private val entryB = SparseSearcher.SafetyEntry("B","baz foo","r2")
    private val entryC = SparseSearcher.SafetyEntry("C","nothing","r3")

    @Test
    fun `score orders by raw matches and weight`() {
        val tokens = listOf("foo")
        val results = SparseScorer.score(listOf(entryA, entryB, entryC), tokens)
        // both A and B contain 'foo', A appears first since no weight difference
        assertEquals(listOf(entryA, entryB), results.take(2))
    }

    @Test
    fun `scoreWithBreakdown returns metrics`() {
        val tokens = listOf("foo")
        val breakdown = SparseScorer.scoreWithBreakdown(listOf(entryA, entryB, entryC), tokens)
        // ensure entries correspond and raw counts >0 for matching items
        assertEquals(2, breakdown.size)
        assertTrue(breakdown.all { it.raw > 0 })
        assertTrue(breakdown[0].final >= breakdown[1].final)
    }

    @Test
    fun `score omits entries with zero raw matches`() {
        val tokens = listOf("zzz")
        val results = SparseScorer.score(listOf(entryA, entryB), tokens)
        assertTrue(results.isEmpty())
    }
}
