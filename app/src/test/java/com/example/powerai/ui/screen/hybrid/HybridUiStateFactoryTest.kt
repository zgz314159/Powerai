package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridUiStateFactoryTest {

    @Test
    fun `submissionStarted updates question and loading state`() {
        val current = HybridUiState(webSearchEnabled = true)

        val updated = HybridUiStateFactory.submissionStarted(
            current = current,
            question = "原始问题",
            sanitizedQuestion = "标准化问题",
            askedAtMillis = 123L
        )

        assertEquals("原始问题", updated.question)
        assertEquals("标准化问题", updated.sanitizedQuestion)
        assertTrue(updated.isLoading)
        assertEquals(123L, updated.askedAtMillis)
        assertTrue(updated.webSearchEnabled)
    }

    @Test
    fun `cleared resets transient fields and preserves other flags`() {
        val current = HybridUiState(
            question = "q",
            sanitizedQuestion = "sq",
            answer = "a",
            references = listOf(KnowledgeItem(1L, "t", "c", "s", null, "", emptyList(), null, null)),
            isLoading = true,
            askedAtMillis = 11L,
            webSearchEnabled = true
        )

        val updated = HybridUiStateFactory.cleared(current)

        assertEquals("", updated.question)
        assertEquals("", updated.answer)
        assertTrue(updated.references.isEmpty())
        assertFalse(updated.isLoading)
        assertEquals(null, updated.askedAtMillis)
        assertTrue(updated.webSearchEnabled)
    }
}