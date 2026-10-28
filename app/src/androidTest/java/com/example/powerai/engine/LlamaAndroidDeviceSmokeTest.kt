package com.example.powerai.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.powerai.engine.ai.LlamaCppDeepSeekRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device smoke for KI-21 llama-android path (A: load / stream / stop / Chinese).
 * Requires a `.gguf` under `files/models/`.
 */
@RunWith(AndroidJUnit4::class)
class LlamaAndroidDeviceSmokeTest {
    @Test
    fun load_stream_stop_chinese() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val modelsDir = File(ctx.filesDir, "models")
        val gguf =
            modelsDir.listFiles()
                ?.firstOrNull { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
        assumeTrue("No GGUF under files/models/", gguf != null && gguf!!.length() > 1_000_000L)

        val runtime = LlamaCppDeepSeekRuntime()
        assertTrue("backend", runtime.safeInitBackend("cpu"))
        assertTrue("load ${gguf!!.absolutePath}", runtime.safeInitModel(gguf.absolutePath, useGpu = false))
        assertTrue("ready", runtime.safeIsReady())

        runtime.safeSetSystemPrompt("你是简洁的中文助手。只用中文回答。")

        val chunks = CopyOnWriteArrayList<String>()
        val firstToken = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val collector =
            scope.launch {
                runtime.chunkFlow.collect { token ->
                    chunks.add(token)
                    firstToken.countDown()
                }
            }

        try {
            assertTrue(
                "stream start",
                runtime.safeNativeGenerateStream("用一句话介绍电力系统。", 48),
            )
            assertTrue("first token within 120s", firstToken.await(120, TimeUnit.SECONDS))
            Thread.sleep(800)
            assertTrue("stop", runtime.safeStopGeneration())
            Thread.sleep(500)

            val text = chunks.joinToString("")
            assertTrue("got streamed text: '$text'", text.isNotBlank())
            assertTrue(
                "expect CJK in output: '$text'",
                text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN },
            )
            assertFalse("still generating after stop", runtime.generationMetrics.value.isGenerating)
        } finally {
            collector.cancel()
            assertTrue(runtime.safeUnload())
            assertFalse(runtime.safeIsReady())
        }
    }
}
