package com.example.powerai.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class EmbeddingIoHelperTest {
    private val fakeCtx: Context = org.mockito.Mockito.mock(Context::class.java)
    private lateinit var tmpDir: File
    private val fakeWorkManager: WorkManager = org.mockito.Mockito.mock(WorkManager::class.java)

    @Before
    fun setUp() {
        tmpDir = File(System.getProperty("java.io.tmpdir") ?: "", "embed_test").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        whenever(fakeCtx.filesDir).thenReturn(tmpDir)
    }

    @Test
    fun enqueueForEmbedding_createsMetaAndSchedulesWork() {
        val helper = EmbeddingIoHelper(fakeCtx, baseDir = File(tmpDir, "embeddings"), workManager = fakeWorkManager)
        helper.ensureDirs()
        val item = KnowledgeItem(id = 42L, title = "T", content = "C", source = "s", category = "c", keywords = emptyList())
        helper.enqueueForEmbedding(listOf(item))
        val pending = File(tmpDir, "embeddings/pending").listFiles()
        assertNotNull(pending)
        assertEquals(1, pending!!.size)
        assertTrue(pending[0].name.contains("42"))
        // verify work manager used
        val stringCap = argumentCaptor<String>()
        val policyCap = argumentCaptor<ExistingWorkPolicy>()
        val requestCap = argumentCaptor<OneTimeWorkRequest>()
        verify(fakeWorkManager).enqueueUniqueWork(stringCap.capture(), policyCap.capture(), requestCap.capture())
        assertEquals("powerai_embedding_worker", stringCap.firstValue)
        assertEquals(ExistingWorkPolicy.KEEP, policyCap.firstValue)
    }

    @Test
    fun storeEmbeddingFile_writesBinaryAndJson() {
        val helper = EmbeddingIoHelper(fakeCtx, baseDir = File(tmpDir, "embeddings"), workManager = fakeWorkManager)
        helper.ensureDirs()
        val emb = FloatArray(10) { it.toFloat() }
        val file = helper.storeEmbeddingFile(99L, emb)
        assertNotNull(file)
        assertTrue(file!!.exists())
        // size should be 4*length
        assertEquals(emb.size * 4, file.length().toInt())
        val json = File(tmpDir, "embeddings/99.json")
        assertTrue(json.exists())
        assertTrue(json.readText().contains("\"status\":\"done\""))
    }
}