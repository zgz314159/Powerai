package com.example.powerai.domain.ai

import com.example.powerai.core.model.ObservabilityService
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.usecase.AiStreamRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import com.example.powerai.engine.ai.AiStreamingService as EngineAiStreamingService

/**
 * Characterization tests for request body generation.
 *
 * They pin the exact JSON field order and escaping of the streaming request
 * body (model, stream, messages) for both AiStreamingService copies, plus the
 * message fragment shape produced by AiStreamRequestBuilder. If any of these
 * assertions fail, the wire format changed.
 */
class AiStreamingServiceBodyTest {
    private val appService = AiStreamingService(mock<ObservabilityService>())

    private val engineService = EngineAiStreamingService(mock<ObservabilityService>())

    @Test
    fun `buildBody pins field order and escaping`() {
        val fragments = listOf("{\"role\":\"user\",\"content\":\"hello\"}")
        val body = appService.buildBody("deepseek-chat", fragments)
        val expected =
            "{\"model\":\"deepseek-chat\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"
        assertEquals(expected, body)
    }

    @Test
    fun `buildBody pins stream false and escaped model name`() {
        val body = appService.buildBody("m\"odel", listOf("{}"), false)
        val expected = "{\"model\":\"m\\\"odel\",\"stream\":false,\"messages\":[{}]}"
        assertEquals(expected, body)
    }

    @Test
    fun `buildBody output is identical for the app and engine service`() {
        val fragments = listOf("{\"role\":\"user\",\"content\":\"q\"}")
        assertEquals(
            appService.buildBody("m", fragments, true),
            engineService.buildBody("m", fragments, true),
        )
    }

    @Test
    fun `buildMessageFragments pins role and content json shape`() {
        val turns =
            listOf(
                ChatTurn(id = 1L, question = "Q1", answer = "A1", askedAtMillis = 0),
            )
        val fragments = AiStreamRequestBuilder.buildMessageFragments(turns, "hi \"there\"")
        val expected =
            listOf(
                "{\"role\":\"user\",\"content\":\"Q1\"}",
                "{\"role\":\"assistant\",\"content\":\"A1\"}",
                "{\"role\":\"user\",\"content\":\"hi \\\"there\\\"\"}",
            )
        assertEquals(expected, fragments)
    }
}
