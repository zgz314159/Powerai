package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.usecase.LocalAnswerPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridModeFallbackFactoryTest {

    @Test
    fun `local failure returns empty local result with assessed fallback`() {
        val rawQuestion = "hello"

        val fallback = HybridModeFallbackFactory.localFailure(rawQuestion)

        assertTrue(fallback.query.retrievals.isEmpty())
        assertTrue(fallback.query.items.isEmpty())
        assertEquals(LocalAnswerPlanner.assess(rawQuestion, emptyList()), fallback.assessment)
        assertNull(fallback.gemmaJob)
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