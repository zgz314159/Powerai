package com.example.powerai.ui.screen.hybrid

import android.content.Context
import android.net.Uri
import com.example.powerai.data.importer.DocumentImportManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class HybridViewModelRuntimeSupportTest {

    @Test
    fun `importDocument delegates import and replays current query when present`() {
        val importer = Mockito.mock(DocumentImportManager::class.java)
        val context = Mockito.mock(Context::class.java)
        val support = HybridViewModelRuntimeSupport(importer, context)
        val uri = Mockito.mock(Uri::class.java)

        var replayedQuery: String? = null
        support.importDocument(uri, "hello") { replayedQuery = it }

        verify(importer).importUri(uri)
        assertEquals("hello", replayedQuery)
    }

    @Test
    fun `importDocument skips replay when current query is blank`() {
        val importer = Mockito.mock(DocumentImportManager::class.java)
        val context = Mockito.mock(Context::class.java)
        val support = HybridViewModelRuntimeSupport(importer, context)
        val uri = Mockito.mock(Uri::class.java)

        var callbackInvoked = false
        support.importDocument(uri, "   ") { callbackInvoked = true }

        verify(importer).importUri(uri)
        assertTrue(!callbackInvoked)
    }

    @Test
    fun `reportUnhandledFailure appends crash log entry`() {
        val importer = Mockito.mock(DocumentImportManager::class.java)
        val context = Mockito.mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "hybrid_runtime_${System.nanoTime()}")
        tempDir.mkdirs()
        whenever(context.filesDir).thenReturn(tempDir)
        val support = HybridViewModelRuntimeSupport(importer, context)

        support.reportUnhandledFailure(IllegalStateException("boom"))

        val logFile = tempDir.resolve("rag_crash.log")
        assertTrue(logFile.exists())
        assertTrue(logFile.readText().contains("boom"))
    }
}