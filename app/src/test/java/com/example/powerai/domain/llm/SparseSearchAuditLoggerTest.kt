package com.example.powerai.domain.llm

import com.example.powerai.engine.ai.SparseSearchAuditLogger
import org.junit.Test

class SparseSearchAuditLoggerTest {
    @Test
    fun `logger methods no-op with null context`() {
        // should not throw
        SparseSearchAuditLogger.writeSearchTriggered(null, "foo")
        SparseSearchAuditLogger.writeLastSearch(null, "foo", listOf("foo"))
        SparseSearchAuditLogger.writeHitDetails(null, emptyList())
    }
}
