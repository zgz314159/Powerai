package com.example.powerai.data.importer

import org.junit.Assert.*
import org.junit.Test

class ImportUtilsTest {
    @Test
    fun `sha256Hex returns consistent lowercase hex`() {
        val s = "hello"
        val h1 = ImportUtils.sha256Hex(s)
        val h2 = ImportUtils.sha256Hex(s)
        assertEquals(h1, h2)
        assertTrue(h1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `stableId64 positive and deterministic`() {
        val s = "test"
        val id1 = ImportUtils.stableId64(s)
        val id2 = ImportUtils.stableId64(s)
        assertEquals(id1, id2)
        assertTrue(id1 > 0)
    }
}