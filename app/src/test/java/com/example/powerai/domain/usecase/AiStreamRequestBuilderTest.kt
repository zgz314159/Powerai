package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.chat.ChatTurn
import org.junit.Assert.*
import org.junit.Test

class AiStreamRequestBuilderTest {
    @Test
    fun `buildMessageFragments includes history and user input`() {
        val turns = listOf(
            ChatTurn(id = 1L, question = "Q1", answer = "A1", askedAtMillis = 0),
            ChatTurn(id = 2L, question = "Q2", answer = "", askedAtMillis = 0)
        )
        val fragments = AiStreamRequestBuilder.buildMessageFragments(turns, "Hello")
        // should contain user question from first turn, assistant answer from first, and final user input
        assertTrue(fragments.any { it.contains("Q1") })
        assertTrue(fragments.any { it.contains("A1") })
        assertTrue(fragments.last().contains("Hello"))
        // empty answer turn should not add assistant message
        assertFalse(fragments.any { it.contains("Q2") && it.contains("assistant") })
    }

    @Test
    fun `buildMessageFragments escapes json special chars`() {
        val turns = listOf(
            ChatTurn(id = 1L, question = "Hi \"there\"", answer = "", askedAtMillis = 0)
        )
        val fragments = AiStreamRequestBuilder.buildMessageFragments(turns, "Bye\n")
        // json should escape quotes and newlines
        assertTrue(fragments.first().contains("\\\"there\\\""))
        assertTrue(fragments.last().contains("\\n"))
    }
}
