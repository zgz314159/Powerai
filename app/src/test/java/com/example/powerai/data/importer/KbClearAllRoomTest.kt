package com.example.powerai.data.importer

import android.content.Context
import android.content.res.AssetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.EmbeddingMetadataEntity
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.VisionCacheEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The explicit "clear all knowledge" escape hatch (`DocumentImportManager.clearAllKnowledgeBases`)
 * through the production chain (`StreamingJsonResourceImporter` → real Room). It deletes every row,
 * marker and id-keyed cache — including pre-migration legacy data — then re-imports the bundled
 * assets. It must be all-or-nothing: any failure or cancellation rolls everything back.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class KbClearAllRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var filesDir: File
    private lateinit var manager: DocumentImportManager

    private val assetPath = "kb/atomic/knowledge_base.json"
    private val sha = "d".repeat(64)
    private val declaredSource = "pdf:$sha::doc.pdf"
    private val fileId: String get() = ImportUtils.sha256Hex("asset:$assetPath")
    private val legacyEntityId = 111L

    private val content = linkedMapOf<String, String>()

    private val noEmbedding =
        object : EmbeddingRepository {
            override suspend fun enqueueForEmbedding(items: List<KnowledgeItem>) {}

            override suspend fun storeEmbedding(
                itemId: Long,
                embedding: FloatArray,
            ) {}
        }

    /** Serves [prefix] bytes then fails every further read (unreadable tail). */
    private class FailingTailInputStream(private val prefix: ByteArray) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index < prefix.size) return prefix[index++].toInt() and 0xFF
            throw IOException("tail read failed")
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (len == 0) return 0
            if (index >= prefix.size) throw IOException("tail read failed")
            val n = minOf(len, prefix.size - index)
            System.arraycopy(prefix, index, b, off, n)
            index += n
            return n
        }
    }

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
        filesDir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "kb-clearall-test")
        filesDir.mkdirs()
        assets = mock()
        context = mock()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(assets.list("kb")).thenReturn(arrayOf("atomic"))
        whenever(assets.list("kb/atomic")).thenReturn(arrayOf("knowledge_base.json"))
        whenever(assets.list("kb/atomic/knowledge_base.json")).thenReturn(emptyArray<String>())
        content[assetPath] = validKb()
        whenever(assets.open(assetPath)).thenAnswer {
            ByteArrayInputStream(content.getValue(assetPath).toByteArray())
        }
        manager = buildManager(db.knowledgeDao())
    }

    @After
    fun tearDown() {
        db.close()
        filesDir.deleteRecursively()
    }

    private fun buildManager(dao: KnowledgeDao): DocumentImportManager =
        DocumentImportManager(
            context = context,
            repo = KnowledgeRepositoryImpl(context, dao, noEmbedding),
            dao = dao,
            scanner = AssetImportScanner(context, dao),
            observability = mock<ObservabilityService>(),
            scope = CoroutineScope(Dispatchers.IO + Job()),
            visionCacheDao = db.visionCacheDao(),
            embeddingDao = db.embeddingDao(),
        )

    private fun entry(
        id: String,
        title: String,
    ): String = """{"entryId":"$id","jobTitle":"$title","position":1,"contentMarkdown":"$title body"}"""

    private fun validKb(): String =
        """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","source":"$declaredSource"},""" +
            """"entries":[${entry("e1", "alpha")},${entry("e2", "beta")}]}"""

    private fun truncatedKb(): String = """{"fileMetadata":{"source":"$declaredSource"},"entries":[${entry("e1", "alpha")}"""

    /** A pre-migration install: unattributed rows, a fingerprint-less marker and id-keyed caches. */
    private fun seedLegacyState() =
        runBlocking {
            db.knowledgeDao().upsertBatch(
                listOf(
                    KnowledgeEntity(
                        id = legacyEntityId,
                        title = "legacy",
                        content = "legacy body",
                        contentNormalized = "legacy body",
                        searchContent = "legacy body",
                        source = declaredSource,
                        category = "old",
                        packageId = null,
                    ),
                ),
            )
            db.knowledgeDao().rebuildFts()
            db.knowledgeDao().insertImportedFile(
                ImportedFileEntity(
                    fileId = fileId,
                    fileName = "atomic",
                    timestamp = 1L,
                    status = AssetImportStatus.IMPORTED,
                    contentSha256 = "",
                ),
            )
            db.visionCacheDao().upsert(
                VisionCacheEntity(entityId = legacyEntityId, blockId = "b1", imageUri = null, markdown = "old", updatedAtMs = 1L),
            )
            db.embeddingDao().upsert(
                EmbeddingMetadataEntity(id = legacyEntityId, fileName = declaredSource, status = "done", createdAt = 1L),
            )
        }

    private fun rows(): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().getAll() }

    private fun ftsCount(): Int = runBlocking { db.knowledgeDao().countFts() }

    private fun clearAll() = runBlocking { manager.clearAllKnowledgeBases("kb") }

    private fun state() = manager.rebuildState.value

    @Test
    fun `clear all wipes legacy rows markers and caches then imports bundled assets`() {
        seedLegacyState()
        assertEquals("legacy seeded", 1, rows().size)
        assertEquals("legacy fts", 1, ftsCount())

        val result = clearAll()

        assertTrue("reported success", result is KbRebuildState.Success)
        assertEquals(
            "success reports the real row count even for a pdf-sourced asset",
            2,
            (result as KbRebuildState.Success).knowledgeRows,
        )
        assertEquals("new rows", 2, rows().size)
        assertTrue("every row attributed to the package", rows().all { it.packageId == fileId })
        assertTrue("legacy title gone", rows().none { it.title == "legacy" })
        assertEquals("fts == rows", rows().size, ftsCount())
        assertTrue("marker has fingerprint", !runBlocking { db.knowledgeDao().getImportedFile(fileId) }?.contentSha256.isNullOrBlank())
        assertTrue("vision cache invalidated", runBlocking { db.visionCacheDao().getAllForEntity(legacyEntityId) }.isEmpty())
        assertNull("embedding metadata invalidated", runBlocking { db.embeddingDao().getFileName(legacyEntityId) })
    }

    @Test
    fun `clear all removes the app-private vector index on success`() {
        val index = File(filesDir, "vector_index.bin")
        index.writeText("stale")
        seedLegacyState()

        clearAll()

        assertFalse("vector index removed", index.exists())
    }

    @Test
    fun `failed clear all rolls back and keeps legacy data and caches intact`() {
        seedLegacyState()
        content[assetPath] = truncatedKb()
        whenever(assets.open(assetPath)).thenReturn(FailingTailInputStream(truncatedKb().toByteArray()))

        val result = clearAll()

        assertTrue("reported failure", result is KbRebuildState.Failed)
        assertEquals("legacy row kept", 1, rows().size)
        assertEquals("legacy content kept", "legacy body", rows().first().content)
        assertEquals("fts kept", 1, ftsCount())
        assertEquals(
            "legacy marker kept without fingerprint",
            "",
            runBlocking { db.knowledgeDao().getImportedFile(fileId) }?.contentSha256,
        )
        assertEquals("vision cache kept", 1, runBlocking { db.visionCacheDao().getAllForEntity(legacyEntityId) }.size)
        assertEquals("embedding metadata kept", declaredSource, runBlocking { db.embeddingDao().getFileName(legacyEntityId) })
    }

    @Test
    fun `cancelled clear all rolls back keeps legacy data and reports cancelled`() {
        seedLegacyState()
        whenever(assets.open(assetPath)).thenReturn(CancellingTailInputStream(truncatedKb().toByteArray()))

        var cancelled = false
        try {
            runBlocking { manager.clearAllKnowledgeBases("kb") }
        } catch (c: CancellationException) {
            cancelled = true
        }

        assertTrue("cancellation propagates", cancelled)
        assertEquals("state cancelled", KbRebuildState.Cancelled, state())
        assertEquals("legacy row kept", 1, rows().size)
        assertEquals("fts kept", 1, ftsCount())
        assertEquals("vision cache kept", 1, runBlocking { db.visionCacheDao().getAllForEntity(legacyEntityId) }.size)
    }

    @Test
    fun `retry after failure clears cleanly`() {
        seedLegacyState()
        whenever(assets.open(assetPath)).thenReturn(FailingTailInputStream(truncatedKb().toByteArray()))
        clearAll()
        assertTrue("first attempt failed", state() is KbRebuildState.Failed)
        assertEquals("rolled back to legacy", 1, rows().size)

        // Serve a healthy KB and retry.
        content[assetPath] = validKb()
        whenever(assets.open(assetPath)).thenAnswer {
            ByteArrayInputStream(content.getValue(assetPath).toByteArray())
        }
        val result = clearAll()

        assertTrue("retry succeeded", result is KbRebuildState.Success)
        assertEquals("rebuilt rows", 2, rows().size)
        assertEquals("fts == rows", rows().size, ftsCount())
        assertNotNull("fingerprint written", runBlocking { db.knowledgeDao().getImportedFile(fileId) }?.contentSha256)
    }

    /** Serves [prefix] bytes then cancels mid-import. */
    private class CancellingTailInputStream(private val prefix: ByteArray) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index < prefix.size) return prefix[index++].toInt() and 0xFF
            throw CancellationException("cancelled mid-import")
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (len == 0) return 0
            if (index >= prefix.size) throw CancellationException("cancelled mid-import")
            val n = minOf(len, prefix.size - index)
            System.arraycopy(prefix, index, b, off, n)
            index += n
            return n
        }
    }
}
