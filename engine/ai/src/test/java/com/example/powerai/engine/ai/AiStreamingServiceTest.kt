package com.example.powerai.engine.ai

import com.example.powerai.core.model.ObservabilityService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Owner-module characterization tests for the single AI streaming service.
 *
 * They pin the request body shape, the Retrofit-style request (endpoint,
 * method, headers, body), SSE chunk extraction, the [DONE] marker, data
 * delivery, error propagation and cancellation.
 */
class AiStreamingServiceTest {
    private lateinit var server: MockWebServer

    private lateinit var observability: RecordingObservability

    private lateinit var service: AiStreamingService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        observability = RecordingObservability()
        service = AiStreamingService(observability)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `buildBody pins field order and escaping`() {
        val fragments = listOf("{\"role\":\"user\",\"content\":\"hello\"}")
        val body = service.buildBody("deepseek-chat", fragments)
        val expected =
            "{\"model\":\"deepseek-chat\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"
        assertEquals(expected, body)
    }

    @Test
    fun `buildBody pins stream false and escaped model name`() {
        val body = service.buildBody("m\"odel", listOf("{}"), false)
        val expected = "{\"model\":\"m\\\"odel\",\"stream\":false,\"messages\":[{}]}"
        assertEquals(expected, body)
    }

    @Test
    fun `buildBody joins multiple messages in order`() {
        val fragments =
            listOf(
                "{\"role\":\"user\",\"content\":\"a\"}",
                "{\"role\":\"assistant\",\"content\":\"b\"}",
            )
        val body = service.buildBody("m", fragments)
        val expected =
            "{\"model\":\"m\",\"stream\":true,\"messages\":[" +
                "{\"role\":\"user\",\"content\":\"a\"}," +
                "{\"role\":\"assistant\",\"content\":\"b\"}]}"
        assertEquals(expected, body)
    }

    @Test
    fun `buildBody embeds pre-escaped fragments verbatim`() {
        val fragment = "{\"role\":\"user\",\"content\":\"say \\\"hi\\\"\"}"
        val body = service.buildBody("m", listOf(fragment))
        assertEquals("{\"model\":\"m\",\"stream\":true,\"messages\":[$fragment]}", body)
    }

    @Test
    fun `createRequest pins endpoint method headers and body`() {
        val body = service.buildBody("m", listOf("{\"role\":\"user\",\"content\":\"q\"}"))
        val request = service.createRequest("https://api.example.test/chat/completions", "secret", body)
        assertEquals("POST", request.method)
        assertEquals("https://api.example.test/chat/completions", request.url.toString())
        assertEquals("Bearer secret", request.header("Authorization"))
        assertEquals("application/json", request.header("Content-Type"))
        assertEquals("text/event-stream", request.header("Accept"))
        val sink = Buffer()
        assertNotNull(request.body)
        request.body!!.writeTo(sink)
        assertEquals(body, sink.readUtf8())
    }

    @Test
    fun `createRequest drops the accept header when streaming is off`() {
        val request =
            service.createRequest("https://api.example.test/chat/completions", "k", "{}", acceptStream = false)
        assertNull(request.header("Accept"))
        assertEquals("Bearer k", request.header("Authorization"))
    }

    @Test
    fun `extractTextChunk reads delta and message content`() {
        val delta = "{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}"
        assertEquals("Hi", service.extractTextChunk(delta))
        val message = "{\"choices\":[{\"message\":{\"content\":\"Answer\"}}]}"
        assertEquals("Answer", service.extractTextChunk(message))
    }

    @Test
    fun `extractTextChunk unescapes content from a chunk`() {
        val chunk = "{\"choices\":[{\"delta\":{\"content\":\"say \\\"hi\\\"\"}}]}"
        assertEquals("say \"hi\"", service.extractTextChunk(chunk))
    }

    @Test
    fun `extractTextChunk returns null for malformed payloads`() {
        assertNull(service.extractTextChunk(""))
        assertNull(service.extractTextChunk("{\"choices\":"))
    }

    @Test
    fun `isSseDoneMarker detects the done marker and finish reason`() {
        assertTrue(service.isSseDoneMarker("data: [DONE]"))
        assertTrue(service.isSseDoneMarker("[done]"))
        assertTrue(service.isSseDoneMarker("{\"choices\":[{\"finish_reason\":\"stop\"}]}"))
    }

    @Test
    fun `isSseDoneMarker ignores blank malformed and in-progress payloads`() {
        assertFalse(service.isSseDoneMarker(""))
        assertFalse(service.isSseDoneMarker("<<<>>>"))
        assertFalse(service.isSseDoneMarker("{\"choices\":[{\"delta\":{\"content\":\"x\"}}]}"))
        assertFalse(service.isSseDoneMarker("{\"choices\":[{\"finish_reason\":null}]}"))
    }

    @Test
    fun `startStreaming delivers sse events and reports the request`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\ndata: [DONE]\n\n"),
        )
        val received = LinkedBlockingQueue<String>()
        val closed = CountDownLatch(1)
        val body = service.buildBody("m", listOf("{\"role\":\"user\",\"content\":\"q\"}"))
        val request = service.createRequest(server.url("/chat/completions").toString(), "key", body)
        val eventSource =
            service.startStreaming(
                request = request,
                onData = { received.add(it) },
                onClosed = { closed.countDown() },
            )
        assertEquals("{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}", received.poll(5, TimeUnit.SECONDS))
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("POST", recorded!!.method)
        assertEquals("/chat/completions", recorded.path)
        assertEquals("Bearer key", recorded.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals(body, recorded.body.readUtf8())
        eventSource.cancel()
    }

    @Test
    fun `startStreaming propagates transport failures through onFailure`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val failures = LinkedBlockingQueue<String>()
        val request = service.createRequest(server.url("/chat/completions").toString(), "k", "{}")
        service.startStreaming(
            request = request,
            onData = {},
            onFailure = { message, _ -> failures.add(message) },
        )
        val message = failures.poll(5, TimeUnit.SECONDS)
        assertNotNull("onFailure must fire when the connection drops", message)
        assertTrue(message!!.isNotBlank())
        // aiCallFinished runs right after the callback on the streaming thread
        assertTrue(observability.finishedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(observability.finished.contains(false))
    }

    @Test
    fun `cancel stops the stream before data arrives`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"late\"}}]}\n\n")
                .setBodyDelay(1, TimeUnit.SECONDS),
        )
        val received = LinkedBlockingQueue<String>()
        val request = service.createRequest(server.url("/chat/completions").toString(), "k", "{}")
        val eventSource =
            service.startStreaming(
                request = request,
                onData = { received.add(it) },
            )
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        eventSource.cancel()
        // The delayed body would arrive about one second after the request;
        // a cancelled stream must not deliver it.
        assertNull(received.poll(2500, TimeUnit.MILLISECONDS))
    }
}

private class RecordingObservability : ObservabilityService {
    val finished = mutableListOf<Boolean>()

    val finishedLatch = CountDownLatch(1)

    override fun importStarted(
        fileId: String,
        fileName: String,
    ) = Unit

    override fun importProgress(
        fileId: String,
        fileName: String,
        importedItems: Long,
        totalItems: Long?,
    ) = Unit

    override fun importCompleted(
        fileId: String,
        fileName: String,
        importedItems: Long,
    ) = Unit

    override fun importFailed(
        fileId: String,
        fileName: String?,
        reason: String?,
    ) = Unit

    override fun retrievalStarted(query: String) = Unit

    override fun retrievalFinished(
        query: String,
        returned: Int,
        durationMs: Long,
    ) = Unit

    override fun aiCallStarted(
        endpoint: String,
        meta: String?,
    ) = Unit

    override fun aiCallFinished(
        endpoint: String,
        durationMs: Long,
        success: Boolean,
        meta: String?,
    ) {
        finished += success
        finishedLatch.countDown()
    }

    override fun logEvent(
        key: String,
        message: String,
    ) = Unit
}
