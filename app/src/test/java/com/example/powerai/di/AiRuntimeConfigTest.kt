package com.example.powerai.di

import com.example.powerai.domain.usecase.AiStreamConfigHelper
import com.example.powerai.engine.ai.AiApiService
import com.example.powerai.engine.ai.ApiChatCompletionsRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Characterization tests for the runtime AI configuration contract.
 *
 * They prove that streaming and vision validation consume the same
 * [com.example.powerai.core.repository.RemoteConfigRepository] instance, that
 * the configured and unavailable paths behave as specified, and that
 * configuration values never surface in errors, stub responses or string
 * representations. Only fake values are used; no network is opened.
 */
class AiRuntimeConfigTest {
    @Test
    fun `runtime config returns the injected configuration`() {
        val config =
            AiRuntimeConfig(
                baseUrl = "https://fake.example.test/",
                apiKey = "fake-key-0",
                deepSeekModel = "fake-model-0",
                debug = true,
                debugStreamUrl = "https://fake.example.test/debug",
            )
        assertEquals("https://fake.example.test/", config.getAiBaseUrl())
        assertEquals("fake-key-0", config.getAiApiKey())
        assertEquals("fake-model-0", config.getDeepSeekModel())
        assertTrue(config.isDebug())
        assertEquals("https://fake.example.test/debug", config.getDebugStreamUrl())
    }

    @Test
    fun `streaming validation and vision provider read the same config`() {
        val config =
            AiRuntimeConfig(
                baseUrl = "https://fake.example.test",
                apiKey = "fake-key-1",
                deepSeekModel = "fake-model-1",
                debug = false,
                debugStreamUrl = null,
            )
        val (url, model) = AiStreamConfigHelper.validateApiConfig(config)
        assertEquals("https://fake.example.test/chat/completions", url)
        assertEquals("fake-model-1", model)
        val visionService = NetworkModule.provideVisionValidationAiApiService(config)
        assertTrue(
            "vision validation must use the real implementation",
            Proxy.isProxyClass(visionService.javaClass),
        )
    }

    @Test
    fun `missing base url selects the unavailable paths`() {
        val config = AiRuntimeConfig("", "fake-key-2", "fake-model-2", false, null)
        val error =
            assertThrows(IllegalStateException::class.java) {
                AiStreamConfigHelper.validateApiConfig(config)
            }
        assertEquals(
            "AI 未配置：请在本地通过 Gradle 配置 AI_BASE_URL（或在 local.properties 中设置）",
            error.message,
        )
        val visionService = NetworkModule.provideVisionValidationAiApiService(config)
        assertFalse(
            "vision validation must fall back to the stub",
            Proxy.isProxyClass(visionService.javaClass),
        )
        val content = stubContent(visionService)
        assertTrue("stub must explain that AI is not configured", content.contains("AI 未配置"))
    }

    @Test
    fun `configuration values never appear in errors stubs or representations`() {
        val secretKey = "fake-secret-key-do-not-leak"
        val secretUrl = "https://fake-secret-host.invalid"
        val configured = AiRuntimeConfig(secretUrl, secretKey, "fake-model-3", false, null)
        assertFalse(configured.toString().contains(secretKey))
        assertFalse(configured.toString().contains(secretUrl))

        val unconfigured = AiRuntimeConfig("", secretKey, "fake-model-3", false, null)
        val error =
            assertThrows(IllegalStateException::class.java) {
                AiStreamConfigHelper.validateApiConfig(unconfigured)
            }
        assertFalse(error.message.orEmpty().contains(secretKey))
        assertFalse(error.message.orEmpty().contains(secretUrl))

        val content = stubContent(NetworkModule.provideVisionValidationAiApiService(unconfigured))
        assertFalse(content.contains(secretKey))
        assertFalse(content.contains(secretUrl))
    }

    private fun stubContent(service: AiApiService): String {
        val response =
            runBlocking {
                service.chatCompletions(
                    ApiChatCompletionsRequest(model = "m", messages = emptyList(), stream = false),
                )
            }
        return response.choices?.firstOrNull()?.message?.content.orEmpty()
    }
}
