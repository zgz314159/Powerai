package com.example.powerai.engine.ai

import com.example.powerai.core.model.SourceQualification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparseSearcherLoaderSourceTest {
    @Test
    fun `builtin fallback entries do not claim formal regulation title`() {
        val entries = DefaultSparseSearcherLoader().loadEntries(context = null)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.source == SourceQualification.UNVERIFIED_SYNOPSIS_LABEL })
        assertFalse(entries.any { it.source == "电力安全工作规程" })
    }

    @Test
    fun `unverified label constant is stable`() {
        assertEquals("内置安规摘要（来源未核定）", SourceQualification.UNVERIFIED_SYNOPSIS_LABEL)
    }
}
