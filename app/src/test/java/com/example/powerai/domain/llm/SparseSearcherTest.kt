package com.example.powerai.domain.llm

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.engine.ai.SparseSearcherLoader

class SparseSearcherTest {
    @Test
    fun `search returns builtins when no context`() {
        val searcher = SparseSearcher(context = null)
        val results = searcher.search("验电", topN = 5)
        // built-in list contains two entries, ensure at least one result is returned
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `search query with no match returns empty`() {
        val searcher = SparseSearcher(context = null)
        val results = searcher.search("zzzzzz", topN = 3)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `inject custom loader yields expected entries`() {
        val fakeEntries = listOf(
            SparseSearcher.SafetyEntry("A","abc","src"),
            SparseSearcher.SafetyEntry("B","bcd","src2")
        )
        val loader = object : SparseSearcherLoader {
            override fun loadEntries(context: Context?) = fakeEntries
        }
        val searcher = SparseSearcher(context = null, loader = loader)
        val res = searcher.search("abc", topN = 2)
        assertEquals(1, res.size)
        assertEquals("A", res[0].title)
    }
}
