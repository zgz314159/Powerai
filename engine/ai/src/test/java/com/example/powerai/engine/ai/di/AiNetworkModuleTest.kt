package com.example.powerai.engine.ai.di

import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.engine.ai.ApiChatCompletionsRequest
import com.example.powerai.engine.ai.ApiChatMessage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

/**
 * Owner-module tests for the AI network provider configuration paths.
 *
 * They prove that a configured base URL selects the real Retrofit
 * implementation, that missing configuration selects the stable stub, and
 * that a blank API key keeps the real endpoint without an Authorization
 * header (the API key is optional by product contract). Only fake configs and
 * a local MockWebServer are used.
 */
class AiNetworkModuleTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fakeConfig(
        baseUrl: String,
        apiKey: String,
    ): RemoteConfigRepository =
        object : RemoteConfigRepository {
            override fun getAiBaseUrl(): String = baseUrl

            override fun getAiApiKey(): String = apiKey

            override fun getDeepSeekModel(): String = "fake-model"

            override fun isDebug(): Boolean = false

            override fun getDebugStreamUrl(): String? = null
        }

    private fun chatRequest(): ApiChatCompletionsRequest =
        ApiChatCompletionsRequest(
            model = "fake-model",
            messages = listOf(ApiChatMessage(role = "user", content = "hi")),
            stream = false,
        )

    private fun enqueuedChatResponse() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"resp-1\",\"choices\":[]}"),
        )
    }

    @Test
    fun `configured base url selects the real retrofit implementation`() {
        enqueuedChatResponse()
        val service = AiNetworkModule.provideAiApiService(fakeConfig(server.url("/").toString(), "fake-key-123"))
        assertTrue(
            "configured service must be the Retrofit implementation",
            Proxy.isProxyClass(service.javaClass),
        )
        val response = runBlocking { service.chatCompletions(chatRequest()) }
        assertEquals("resp-1", response.id)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/chat/completions", recorded!!.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer fake-key-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `blank base url selects the stub with a stable empty response`() {
        val service = AiNetworkModule.provideAiApiService(fakeConfig("", "fake-key-123"))
        assertFalse(
            "missing base url must select the stub",
            Proxy.isProxyClass(service.javaClass),
        )
        val response = runBlocking { service.chatCompletions(chatRequest()) }
        assertTrue("stub must return no choices", response.choices.orEmpty().isEmpty())
        assertEquals("stub must not open a connection", 0, server.requestCount)
    }

    @Test
    fun `blank api key keeps the real endpoint without an authorization header`() {
        enqueuedChatResponse()
        val service = AiNetworkModule.provideAiApiService(fakeConfig(server.url("/").toString(), ""))
        assertTrue(
            "an optional api key must not disable the real implementation",
            Proxy.isProxyClass(service.javaClass),
        )
        runBlocking { service.chatCompletions(chatRequest()) }
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/chat/completions", recorded!!.path)
        assertNull("keyless setups must not send Authorization", recorded.getHeader("Authorization"))
    }
}
