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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * Same asset path, KB content updated: the production import chain
 * (`StreamingJsonResourceImporter` → `DocumentImportManager.importAssetsIfNeed` → real Room) must
 * recognise the new version and replace **only that asset package's** rows, so deleted entries
 * disappear from the list/FTS while other packages stay untouched — even when they declare the
 * same `source`/`docSha256`.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class KbAssetUpdateReplaceRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var manager: DocumentImportManager

    private val atomicPath = "kb/atomic/knowledge_base.json"
    private val otherPath = "kb/other/knowledge_base.json"
    private val sha = "a".repeat(64)
    private val sharedSource = "pdf:$sha::shared.pdf"

    /** Current bytes served on every `assets.open(path)`; mutable to swap versions. */
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
        whenever(assets.list("kb")).thenReturn(arrayOf("atomic", "other"))
        whenever(assets.list("kb/atomic")).thenReturn(arrayOf("knowledge_base.json"))
        whenever(assets.list("kb/atomic/knowledge_base.json")).thenReturn(emptyArray<String>())
        whenever(assets.list("kb/other")).thenReturn(arrayOf("knowledge_base.json"))
        whenever(assets.list("kb/other/knowledge_base.json")).thenReturn(emptyArray<String>())

        content[atomicPath] = versionA()
        content[otherPath] = otherKb()
        whenever(assets.open(atomicPath)).thenAnswer {
            ByteArrayInputStream(content.getValue(atomicPath).toByteArray())
        }
        whenever(assets.open(otherPath)).thenAnswer {
            ByteArrayInputStream(content.getValue(otherPath).toByteArray())
        }

        manager = buildManager(db.knowledgeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildManager(dao: KnowledgeDao): DocumentImportManager =
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
            """"source":"$sharedSource"},"entries":[${entries.joinToString(",")}]}"""

    /** Version A: alpha + beta(old) + gamma. */
    private fun versionA(): String =
        kb(
            entry("e1", "alpha", 1, "alpha body"),
            entry("e2", "beta", 2, "beta old body"),
            entry("e3", "gamma", 3, "gamma body"),
        )

    /** Version B: beta modified (same id), delta added, gamma deleted, same declared source. */
    private fun versionB(): String =
        kb(
            entry("e1", "alpha", 1, "alpha body"),
            entry("e2", "beta", 2, "beta new body"),
            entry("e4", "delta", 4, "delta body"),
        )

    /** A different package that declares the SAME source/docSha256 as atomic. */
    private fun otherKb(): String = kb(entry("o1", "other alpha", 1, "other alpha body"))

    private fun importAll() = runBlocking { manager.importAssetsIfNeed("kb") }

    private fun rows(): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().getAll() }

    private fun ftsCount(): Int = runBlocking { db.knowledgeDao().countFts() }

    private fun ftsHits(match: String): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().searchByFts(match) }

    private fun titles(): List<String> = rows().map { it.title }

    private fun rowByTitle(title: String): KnowledgeEntity? = rows().find { it.title == title }

    @Test
    fun `same path version B replaces package rows and drops deleted entries`() {
        importAll()
        assertEquals("A: atomic 3 + other 1", 4, rows().size)
        assertEquals("A fts", 4, ftsCount())
        assertTrue("A has gamma", rowByTitle("gamma") != null)

        // Swap the same asset path to version B and re-run the production import.
        content[atomicPath] = versionB()
        importAll()

        assertEquals("B: atomic 3 + other 1", 4, rows().size)
        assertEquals("B fts == rows", rows().size, ftsCount())
        assertTrue("deleted gamma must be gone", rowByTitle("gamma") == null)
        assertTrue("added delta present", rowByTitle("delta") != null)
        assertEquals(
            "modified beta content",
            "beta new body",
            rowByTitle("beta")?.content?.trim(),
        )
        assertEquals("old gamma not searchable", 0, ftsHits("gamma").size)
        assertEquals("new delta searchable", 1, ftsHits("delta").size)
        assertEquals("untouched other package", 1, ftsHits("other").size)
    }

    @Test
    fun `other package with identical source and docSha256 is untouched`() {
        importAll()
        val otherBefore = rowByTitle("other alpha")

        content[atomicPath] = versionB()
        importAll()

        val otherAfter = rowByTitle("other alpha")
        assertEquals("other row still present exactly once", 1, titles().count { it == "other alpha" })
        assertEquals("other id unchanged", otherBefore?.id, otherAfter?.id)
        assertEquals("other content unchanged", otherBefore?.content, otherAfter?.content)
        assertEquals("other source unchanged", otherBefore?.source, otherAfter?.source)
    }

    /** Delegates to the real Room DAO (and its transaction) while counting package deletes. */
    private class CountingDeleteDao(
        private val delegate: KnowledgeDao,
    ) : KnowledgeDao by delegate {
        var packageDeletes = 0

        override suspend fun deleteByPackageId(packageId: String): Int {
            packageDeletes++
            return delegate.deleteByPackageId(packageId)
        }
    }

    @Test
    fun `unchanged content is skipped without writes or package deletes`() {
        importAll()
        val rowsBefore = rows().map { it.id to it.content }
        val ftsBefore = ftsCount()

        // Second run with identical bytes, on a DAO that records package deletes.
        val spy = CountingDeleteDao(db.knowledgeDao())
        manager = buildManager(spy)
        importAll()

        assertEquals("rows unchanged", rowsBefore, rows().map { it.id to it.content })
        assertEquals("fts unchanged", ftsBefore, ftsCount())
        assertEquals("no package delete when unchanged", 0, spy.packageDeletes)
        assertEquals(
            "imported marker intact",
            "imported",
            runBlocking { db.knowledgeDao().getImportedFileStatus(ImportUtils.sha256Hex("asset:$atomicPath")) },
        )
    }

    @Test
    fun `imported package fingerprint is the single hash of the asset bytes`() {
        importAll()
        val fileId = ImportUtils.sha256Hex("asset:$atomicPath")
        val stored = runBlocking { db.knowledgeDao().getImportedFile(fileId) }?.contentSha256
        val bytes = versionA().toByteArray(Charsets.UTF_8)

        assertEquals("contentSha256 == SHA-256(asset bytes)", ImportUtils.sha256Hex(bytes), stored)

        // Guard against the double-hash regression: hashing the digest again must not match.
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        assertNotEquals("must not be SHA-256(SHA-256(file))", ImportUtils.sha256Hex(digest), stored)
    }
}
