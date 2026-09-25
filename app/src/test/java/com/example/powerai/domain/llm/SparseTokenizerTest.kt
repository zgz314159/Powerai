package com.example.powerai.domain.llm

import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.engine.ai.SparseTokenizer

class SparseTokenizerTest {
    @Test
    fun `tokenize simple english returns words`() {
        val tokens = SparseTokenizer.tokenize("hello world")
        assertTrue(tokens.contains("hello"))
        assertTrue(tokens.contains("world"))
    }

    @Test
    fun `tokenize chinese generates ngrams`() {
        val tokens = SparseTokenizer.tokenize("检电")
        // should include the full token; n-grams smaller than 2 are not generated
        assertTrue(tokens.contains("检电"))
        assertFalse(tokens.isEmpty())
    }

    @Test
    fun `empty query returns empty list`() {
        assertTrue(SparseTokenizer.tokenize("").isEmpty())
        assertTrue(SparseTokenizer.tokenize("   ").isEmpty())
    }
}