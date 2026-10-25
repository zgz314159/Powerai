package com.example.powerai.ui.screen.hybrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridModeFallbackFactoryTest {

    @Test
    fun `local failure returns a retryable error payload with safe copy`() {
        val fallback = HybridModeFallbackFactory.localFailure("hello")

        assertTrue(fallback.references.isEmpty())
        assertTrue(fallback.evidence.isEmpty())
        assertNull(fallback.gemmaJob)
        assertEquals(LocalPageState.ERROR, fallback.summaryState.pageState)
        assertEquals(LocalSummaryStateFactory.SEARCH_FAILED_MESSAGE, fallback.summaryState.errorMessage)
        assertEquals("hello", fallback.summaryState.query)
    }

    @Test
    fun `ai failure formats throwable message`() {
        val error = IllegalStateException("boom")

        assertEquals("AI error: boom", HybridModeFallbackFactory.aiFailure(error))
    }

    @Test
    fun `smart failure wraps ai style error in query result`() {
        val error = IllegalArgumentException("bad")

        val fallback = HybridModeFallbackFactory.smartFailure(error)

        assertEquals("AI error: bad", fallback.answer)
        assertTrue(fallback.references.isEmpty())
        assertEquals(0f, fallback.confidence)
    }
}