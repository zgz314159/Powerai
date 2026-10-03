package com.example.powerai.data.importer

import android.content.Context
import android.content.res.AssetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.ObservabilityService
import com.example.powerai.core.repository.EmbeddingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * A **failing or cancelled update** of a built-in KB asset must leave the previous version's
 * rows, FTS and "imported" marker (with its fingerprint) fully intact, so the update can be
 * retried and never exposes mixed old/new data.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class KbAssetUpdateResilienceRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetManager

    private val atomicPath = "kb/atomic/knowledge_base.json"
    private val sha = "c".repeat(64)
    private val source = "pdf:$sha::doc.pdf"
    private val fileId: String get() = ImportUtils.sha256Hex("asset:$atomicPath")

    private val content = linkedMapOf<String, String>()

    private val noEmbedding =
        object : EmbeddingRepository {
            override suspend fun enqueueForEmbedding(items: List<KnowledgeItem>) {}

            override suspend fun storeEmbedding(
                itemId: Long,
                embedding: FloatArray,
            ) {}
        }

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
        assets = mock()
        context = mock()
        whenever(context.assets).thenReturn(assets)
        whenever(assets.list("kb")).thenReturn(arrayOf("atomic"))
        whenever(assets.list("kb/atomic")).thenReturn(arrayOf("knowledge_base.json"))
        whenever(assets.list("kb/atomic/knowledge_base.json")).thenReturn(emptyArray<String>())
        content[atomicPath] = versionA()
        whenever(assets.open(atomicPath)).thenAnswer {
            ByteArrayInputStream(content.getValue(atomicPath).toByteArray())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun manager(dao: KnowledgeDao): DocumentImportManager =
        DocumentImportManager(
            context = context,
            repo = KnowledgeRepositoryImpl(context, dao, noEmbedding),
            dao = dao,
            scanner = AssetImportScanner(context, dao),
            observability = mock<ObservabilityService>(),
            scope = CoroutineScope(Dispatchers.IO + Job()),
        )

    private fun entry(
        id: String,
        title: String,
        position: Int,
        text: String,
    ): String = """{"entryId":"$id","jobTitle":"$title","position":$position,"contentMarkdown":"$text"}"""

    private fun kb(vararg entries: String): String =
        """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","docSha256":"$sha",""" +
            """"source":"$source"},"entries":[${entries.joinToString(",")}]}"""

    private fun versionA(): String =
        kb(
            entry("e1", "alpha", 1, "alpha body"),
            entry("e2", "beta", 2, "beta old body"),
        )

    private fun versionB(): String =
        kb(
            entry("e1", "alpha", 1, "alpha body"),
            entry("e2", "beta", 2, "beta new body"),
            entry("e3", "gamma", 3, "gamma body"),
        )

    private fun importWith(dao: KnowledgeDao) = runBlocking { manager(dao).importAssetsIfNeed("kb") }

    private fun rows(): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().getAll() }

    private fun ftsCount(): Int = runBlocking { db.knowledgeDao().countFts() }

    private fun marker() = runBlocking { db.knowledgeDao().getImportedFile(fileId) }

    private fun importVersionA(): String {
        importWith(db.knowledgeDao())
        val hash = marker()?.contentSha256
        assertEquals("A imported", 2, rows().size)
        assertNotNull("A fingerprint stored", hash)
        return hash!!
    }

    private fun assertVersionAIntact(
        hashA: String,
        label: String,
    ) {
        val byTitle = rows().associateBy { it.title }
        assertEquals("$label: both A rows", 2, rows().size)
        assertEquals("$label: beta old kept", "beta old body", byTitle["beta"]?.content)
        assertTrue("$label: gamma absent", byTitle["gamma"] == null)
        assertEquals("$label: fts == A rows", 2, ftsCount())
        val m = marker()
        assertEquals("$label: marker still imported", "imported", m?.status)
        assertEquals("$label: old fingerprint kept", hashA, m?.contentSha256)
    }

    private class FailingBatchDao(private val delegate: KnowledgeDao) : KnowledgeDao by delegate {
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
            throw IOException("batch write failed")
        }
    }

    private class CancellingBatchDao(private val delegate: KnowledgeDao) : KnowledgeDao by delegate {
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
            throw CancellationException("cancelled mid-update")
        }
    }

    private class FailingFtsDao(private val delegate: KnowledgeDao) : KnowledgeDao by delegate {
        override suspend fun rebuildFts() {
            throw IllegalStateException("fts rebuild failed")
        }
    }

    @Test
    fun `batch write failure keeps previous version and retry succeeds`() {
        val hashA = importVersionA()

        content[atomicPath] = versionB()
        importWith(FailingBatchDao(db.knowledgeDao()))

        assertVersionAIntact(hashA, "batch failure")

        // Retry the same update with a healthy DAO: it now replaces cleanly.
        importWith(db.knowledgeDao())
        val byTitle = rows().associateBy { it.title }
        assertEquals("retry: 3 rows", 3, rows().size)
        assertEquals("retry: beta new", "beta new body", byTitle["beta"]?.content)
        assertNotNull("retry: gamma present", byTitle["gamma"])
        assertEquals("retry: fts == rows", rows().size, ftsCount())
    }

    @Test
    fun `fts rebuild failure keeps previous version`() {
        val hashA = importVersionA()

        content[atomicPath] = versionB()
        importWith(FailingFtsDao(db.knowledgeDao()))

        assertVersionAIntact(hashA, "fts failure")
    }

    @Test
    fun `cancellation during update propagates and keeps previous version`() {
        val hashA = importVersionA()

        content[atomicPath] = versionB()
        var cancelled = false
        try {
            importWith(CancellingBatchDao(db.knowledgeDao()))
        } catch (c: CancellationException) {
            cancelled = true
        }

        assertTrue("cancellation must propagate", cancelled)
        assertVersionAIntact(hashA, "cancellation")
    }
}
