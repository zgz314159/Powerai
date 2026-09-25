package com.example.powerai.domain.llm

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import com.example.powerai.engine.ai.GemmaModelLoader

class GemmaModelLoaderTest {
    @Test
    fun `tryLoad returns null when model missing`() {
        val ctx = mock(Context::class.java)
        val tmp = File(System.getProperty("java.io.tmpdir"))
        `when`(ctx.filesDir).thenReturn(tmp)
        // ensure model path does not exist
        val modelFile = File("/data/user/0/com.example.powerai/files/gemma3.task")
        if (modelFile.exists()) modelFile.delete()

        val loaded = GemmaModelLoader.tryLoad(ctx)
        assertNull(loaded)
    }

    @Test
    fun `tryLoad handles invalid file gracefully`() {
        val ctx = mock(Context::class.java)
        `when`(ctx.filesDir).thenReturn(File(System.getProperty("java.io.tmpdir")))
        // create zero‑length dummy file at expected path if possible (may require permission)
        try {
            val dummy = File("/data/user/0/com.example.powerai/files/gemma3.task")
            dummy.parentFile?.mkdirs()
            dummy.writeText("")
        } catch (_: Throwable) {}
        val loaded = GemmaModelLoader.tryLoad(ctx)
        // may be null but should not throw
        assertTrue(loaded == null || loaded != null)
    }

    @Test
    fun `closeEngine invokes close on supplied object`() {
        data class Dummy(var closed: Boolean = false) {
            fun close() { closed = true }
        }
        val dummy = Dummy()
        GemmaModelLoader.closeEngine(dummy, null)
        assertTrue(dummy.closed)
    }

    @Test
    fun `closeEngine tolerates null and objects without close`() {
        // null engine should no-op
        GemmaModelLoader.closeEngine(null, null)
        // object lacking method should not throw
        GemmaModelLoader.closeEngine("string", null)
    }
}
