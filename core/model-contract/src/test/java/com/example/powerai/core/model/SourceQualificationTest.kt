package com.example.powerai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceQualificationTest {
    private fun item(
        source: String,
        category: String = "安规",
    ) = KnowledgeItem(
        id = 1L,
        title = "t",
        content = "c",
        source = source,
        category = category,
        keywords = emptyList(),
    )

    @Test
    fun `legacy formal name is treated as unverified synopsis`() {
        assertTrue(SourceQualification.isUnverifiedSynopsis(item("电力安全工作规程")))
        assertFalse(SourceQualification.isQualifiedForNormativeAnswer(item("电力安全工作规程")))
    }

    @Test
    fun `honest label and builtin-synopsis category are unverified`() {
        assertTrue(SourceQualification.isUnverifiedSynopsis(item(SourceQualification.UNVERIFIED_SYNOPSIS_LABEL)))
        assertTrue(SourceQualification.isUnverifiedSynopsis(item("某包", category = "builtin-synopsis")))
    }

    @Test
    fun `real package source remains qualified`() {
        val real = item("铁运[1999]103号铁路电力安全工作规程")
        assertTrue(SourceQualification.isQualifiedForNormativeAnswer(real))
    }

    @Test
    fun `partition keeps only qualified for normative path`() {
        val (q, u) =
            SourceQualification.partitionForNormativeAnswer(
                listOf(
                    item("电力安全工作规程"),
                    item("铁运安规"),
                ),
            )
        assertEquals(1, q.size)
        assertEquals(1, u.size)
        assertTrue(SourceQualification.unverifiedSynopsisNotice(u).contains("未核定"))
    }
}
