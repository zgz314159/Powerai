package com.example.powerai.worker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.powerai.data.worker.EmbeddingWorker
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EmbeddingWorkerInstrumentedTest {
    private lateinit var ctx: Context
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try { server.shutdown() } catch (_: Throwable) {}
    }

    @Test
    fun workerProcessesPendingAndWritesSuccessMetrics() {
        // prepare prefs: use http mode and point to mock server
        val prefs = ctx.getSharedPreferences("powerai_prefs", Context.MODE_PRIVATE)
        val url = server.url("/embed_batch").toString()
        prefs.edit().putString("embedding_service_mode", "http").putString("embedding_service_url", url).apply()

        // prepare pending JSON for id 1111 (start from a clean embeddings dir
        // so stale metrics from previous runs cannot affect assertions)
        val base = File(ctx.filesDir, "embeddings")
        base.deleteRecursively()
        val pending = File(base, "pending")
        pending.mkdirs()
        val pendingFile = File(pending, "1111.json")
        val payload = "{\"id\":\"1111\",\"title\":\"T\",\"content\":\"Hello embedding test\"}"
        pendingFile.writeText(payload)

        // prepare mock response: returns a 384-dim embedding vector for id 1111
        val embeddingJson = List(384) { "0.1" }.joinToString(",")
        val respBody = """{"results":{"1111":[$embeddingJson]}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(respBody))

        // enqueue the worker (same unique name used by EmbeddingRepositoryImpl)
        val work = OneTimeWorkRequestBuilder<EmbeddingWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("powerai_embedding_worker", ExistingWorkPolicy.REPLACE, work)

        // wait for work to finish (polling the unique work info)
        val wm = WorkManager.getInstance(ctx)
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20)
        var finished = false
        while (System.currentTimeMillis() < deadline) {
            val infos = wm.getWorkInfosForUniqueWork("powerai_embedding_worker").get()
            if (infos.isNotEmpty()) {
                val state = infos[0].state
                if (state.isFinished) { finished = true; break }
            }
            Thread.sleep(200)
        }
        assertTrue("Work did not finish within timeout", finished)

        // assert metrics file exists and contains an entry
        val metrics = File(base, "embedding_worker_metrics.log")
        assertTrue("metrics file missing", metrics.exists())
        val lines = metrics.readLines().filter { it.isNotBlank() }
        assertTrue("metrics empty", lines.isNotEmpty())

        // assert the last metrics entry reports a clean run for the single pending item
        val metric = org.json.JSONObject(lines.last())
        assertEquals("unexpected processed count", 1, metric.getInt("processed"))
        assertEquals("embedding processing reported failures", 0, metric.getInt("failures"))

        // assert the pending file was consumed by the worker
        assertFalse("pending file was not removed", pendingFile.exists())
    }
}
