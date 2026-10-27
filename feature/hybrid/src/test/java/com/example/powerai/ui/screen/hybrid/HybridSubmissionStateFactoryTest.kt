package com.example.powerai.ui.screen.hybrid

import com.example.powerai.ui.screen.main.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSubmissionStateFactoryTest {

    @Test
    fun `prepare local mode clears local surfaces and builds summary state`() {
        val current = HybridUiState(question = "old")

        val prepared = HybridSubmissionStateFactory.prepare(
            current = current,
            question = "  hello world  ",
            mode = DisplayMode.LOCAL,
            askedAtMillis = 123L
        )

        assertEquals("  hello world  ", prepared.sanitizedQuestion)
        assertEquals(HybridUtils.normalizeForFts("  hello world  "), prepared.ftsQuery)
        assertTrue(prepared.clearAiAnswer)
        assertTrue(prepared.clearEvidence)
        assertTrue(prepared.resetLocalUi)
        assertFalse(prepared.resetSmartUi)
        assertNotNull(prepared.localSummaryState)
        assertEquals("  hello world  ", prepared.localSummaryState?.query)
        assertEquals("  hello world  ", prepared.nextUiState.question)
        assertEquals("  hello world  ", prepared.nextUiState.sanitizedQuestion)
        assertTrue(prepared.nextUiState.isLoading)
    }

    @Test
    fun `prepare smart mode resets smart controls only`() {
        val prepared = HybridSubmissionStateFactory.prepare(
            current = HybridUiState(),
            question = "smart q",
            mode = DisplayMode.SMART,
            askedAtMillis = 456L
        )

        assertFalse(prepared.clearAiAnswer)
        assertFalse(prepared.clearEvidence)
        assertFalse(prepared.resetLocalUi)
        assertTrue(prepared.resetSmartUi)
        assertNull(prepared.localSummaryState)
        assertEquals("smart q", prepared.sanitizedQuestion)
    }

    @Test
    fun `prepare ai mode only updates shared submission state`() {
        val prepared = HybridSubmissionStateFactory.prepare(
            current = HybridUiState(webSearchEnabled = true),
            question = "ai q",
            mode = DisplayMode.AI,
            askedAtMillis = 789L
        )

        assertFalse(prepared.clearAiAnswer)
        assertFalse(prepared.clearEvidence)
        assertFalse(prepared.resetLocalUi)
        assertFalse(prepared.resetSmartUi)
        assertNull(prepared.localSummaryState)
        assertTrue(prepared.nextUiState.webSearchEnabled)
        assertEquals("ai q", prepared.nextUiState.sanitizedQuestion)
    }
}