package com.example.powerai.ui.screen.hybrid

import com.example.powerai.data.local.LocalSearchEntry
import org.junit.Assert.*
import org.junit.Test

class HybridUtilsTest {
    @Test
    fun `sanitize should fix ISO-8859-1 mojibake`() {
        // 中文字符在误解码后变成 ? 连续的情况
        val bad = "??" + "测试" // uninterpreted scenario
        val result = HybridUtils.sanitizeQuestion(bad)
        // if no better conversion, original preserved
        assertEquals(bad, result)

        // simulate a common mojibake: start with a UTF8 string and interpret
        // its bytes as ISO-8859-1 text. the result is a gibberish string that does
        // not contain '?' but should be recoverable by our heuristic.
        val original = "你好"
        val messed = String(original.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        // sanity check that we really mangled it
        assertNotEquals(original, messed)
        // sanitize should undo the damage and return the original text
        assertEquals(original, HybridUtils.sanitizeQuestion(messed))
    }

    @Test
    fun `normalizeForFts adds wildcard and lowercases`() {
        assertEquals("hello*", HybridUtils.normalizeForFts("Hello"))
        assertEquals("abc*", HybridUtils.normalizeForFts("abc*"))
    }

    @Test
    fun `appendSearchHistory dedups and caps`() {
        val base = listOf(
            LocalSearchEntry("one", 1),
            LocalSearchEntry("two", 2)
        )
        val added = HybridUtils.appendSearchHistory(base, "one")
        assertEquals(2, added.size)
        assertEquals("one", added[0].query)

        // cap size
        val longList = (1..55).map { LocalSearchEntry("q$it", it.toLong()) }
        val capped = HybridUtils.appendSearchHistory(longList, "new")
        assertEquals(50, capped.size)
    }
}