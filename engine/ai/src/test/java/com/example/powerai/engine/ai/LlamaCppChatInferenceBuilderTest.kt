package com.example.powerai.engine.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppChatInferenceBuilderTest {
    @Test
    fun `folds system into single user message without system role`() {
        val messages =
            LlamaCppChatInferenceBuilder.buildMessages(
                systemPrompt = "速答助手",
                userPrompt = "用户问题：10KV如何验电",
            )
        assertEquals(1, messages.size)
        assertEquals("user", messages.single().role)
        assertTrue(messages.single().content.contains("速答助手"))
        assertTrue(messages.single().content.contains("10KV如何验电"))
        assertFalse(messages.any { it.role.equals("system", ignoreCase = true) })
    }

    @Test
    fun `blank system leaves user content unchanged`() {
        val messages =
            LlamaCppChatInferenceBuilder.buildMessages(
                systemPrompt = "  ",
                userPrompt = "仅用户",
            )
        assertEquals("仅用户", messages.single().content)
    }

    @Test
    fun `diagnostics declare messages template mode and sampling`() {
        val json =
            LlamaCppChatInferenceBuilder.diagnosticsJson(
                systemPrompt = "sys",
                userPrompt = "ask",
                maxTokens = 128,
                stream = true,
                nThreads = 4,
                nCtx = 2048,
                nBatch = 512,
                modelPath = "/models/x.gguf",
            )
        assertTrue(json.contains("\"templateMode\":\"${LlamaCppChatInferenceBuilder.TEMPLATE_MODE}\""))
        assertTrue(json.contains("\"messageRoles\":[\"user\"]"))
        assertTrue(json.contains("\"systemFoldedIntoUser\":true"))
        assertTrue(json.contains("\"temperature\":0.6"))
        assertTrue(json.contains("\"top_p\":0.95"))
        assertTrue(json.contains("\"stream\":true"))
        assertFalse(json.contains("InferenceParameters.of"))
    }

    @Test
    fun `buildInferenceParameters uses messages path not raw of`() {
        val params =
            LlamaCppChatInferenceBuilder.buildInferenceParameters(
                systemPrompt = null,
                userPrompt = "hello",
                maxTokens = 64,
                stream = false,
            )
        // toString / JSON 应体现 messages；不得退回纯 prompt-of 拼接作为唯一输入形态
        val rendered = params.toString()
        assertTrue(
            "expected messages in params, got=$rendered",
            rendered.contains("messages", ignoreCase = true) ||
                rendered.contains("\"role\"") ||
                rendered.contains("user"),
        )
    }
}
