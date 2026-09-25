package com.example.powerai.data.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.model.ObservabilityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentImportManagerTest {
    private lateinit var context: Context
    private lateinit var repo: KnowledgeRepository
    private lateinit var dao: KnowledgeDao
    private lateinit var scanner: AssetImportScanner
    private lateinit var observability: ObservabilityService
    private lateinit var manager: DocumentImportManager

    @Before
    fun setup() {
        context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        repo = mock()
        dao = mock()
        scanner = mock()
        observability = mock()
    }

    @Test
    fun `importUri delegates to parser factory and updates progress`() = runTest {
        val fakeParser = object : FileParser {
            override suspend fun parse(
                uri: Uri,
                fileName: String,
                batchSize: Int,
                onBatchReady: suspend (List<KnowledgeEntity>) -> Unit
            ): String {
                onBatchReady(listOf(KnowledgeEntity(title = "t", content = "c", source = "s", category = "cat")))
                return "file123"
            }
        }

        val factory = object : FileParserFactoryType {
            override fun create(fileName: String, contentResolver: ContentResolver): FileParser = fakeParser
        }

        manager = DocumentImportManager(
            context = context,
            repo = repo,
            dao = dao,
            scanner = scanner,
            observability = observability,
            scope = this,
            parserFactory = factory
        )

        val uri: Uri = mock()
        whenever(uri.lastPathSegment).thenReturn("hello.txt")
        whenever(repo.isFileImported("file123")).thenReturn(false)

        manager.importUri(uri, batchSize = 1)
        advanceUntilIdle()

        verify(repo).insertBatch(any())
        verify(repo).markFileImported(eq("file123"), eq("hello.txt"), any(), eq("imported"))
        verify(observability).importStarted(eq("file123"), eq("hello.txt"))
        verify(observability).importCompleted(eq("file123"), eq("hello.txt"), eq(0))

        val last = manager.progress.value
        assertEquals("imported", last?.status)
        assertEquals("file123", last?.fileId)
    }
}
