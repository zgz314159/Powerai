package com.example.powerai.domain.llm

import org.junit.Test
import com.example.powerai.engine.ai.SparseSearchAuditLogger

class SparseSearchAuditLoggerTest {
    @Test
    fun `logger methods no-op with null context`() {
        // should not throw
        SparseSearchAuditLogger.writeSearchTriggered(null, "foo")
        SparseSearchAuditLogger.writeLastSearch(null, "foo", listOf("foo"))
        SparseSearchAuditLogger.writeHitDetails(null, emptyList())
    }
}