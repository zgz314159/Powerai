package com.example.powerai.data.importer

import android.content.Context
import android.content.res.AssetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.KnowledgeEntity
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
import org.junit.Assert.assertNotEquals
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
import java.io.IOException
import java.io.InputStream

/**
 * Production KB asset import must be all-or-nothing per file: a read failure or cancellation
 * while streaming `StreamingJsonResourceImporter → DocumentImportManager.importAssetsIfNeed`
 * must leave no searchable rows, no FTS rows and no `imported` marker, and a retry with the same
 * valid KB must then succeed. Uses the real Room database, not an in-memory DAO stub.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class KbImportAtomicityRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var manager: DocumentImportManager

    private val assetPath = "kb/atomic/knowledge_base.json"
    private val sha = "b".repeat(64)
    private val declaredSource = "pdf:$sha::doc.pdf"
    private val fileId: String get() = ImportUtils.sha256Hex("asset:$assetPath")

    private val noEmbedding =
        object : EmbeddingRepository {
            override suspend fun enqueueForEmbedding(items: List<KnowledgeItem>) {}

            override suspend fun storeEmbedding(
                itemId: Long,
                embedding: FloatArray,
            ) {}
        }

    /** Serves [prefix] bytes, then fails every further read (unreadable tail). */
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

    /** Serves [prefix] bytes, then cancels mid-import. */
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

        manager = buildManager(db.knowledgeDao())
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

    /** Delegates to the real Room DAO (including its transaction) but fails batch writes. */
    private class FailingBatchDao(
        private val delegate: KnowledgeDao,
        private val failFromFlush: Int,
    ) : KnowledgeDao by delegate {
        private var flushes = 0

        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
            flushes++
            if (flushes >= failFromFlush) throw IOException("batch write failed")
            delegate.upsertBatchTransactional(entities)
        }
    }

    /** Delegates to the real Room DAO but fails the FTS rebuild. */
    private class FailingFtsDao(private val delegate: KnowledgeDao) : KnowledgeDao by delegate {
        override suspend fun rebuildFts() {
            throw IllegalStateException("fts rebuild failed")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entries(
        count: Int,
        from: Int = 1,
    ): String =
        (from until from + count).joinToString(",") {
            """{"entryId":"e$it","jobTitle":"t$it","position":$it,"contentMarkdown":"content $it"}"""
        }

    private fun fullKb(count: Int): String =
        """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","source":"$declaredSource"},"entries":[${entries(count)}]}"""

    /** Header + [flushed] complete entries with an unterminated tail (read failure follows). */
    private fun truncatedKb(flushed: Int): String =
        """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","source":"$declaredSource"},"entries":[${entries(flushed)}"""

    private fun bytesOf(text: String) = text.toByteArray(Charsets.UTF_8)

    private fun runImport(stream: InputStream) {
        whenever(assets.open(assetPath)).thenReturn(stream)
        runBlocking { manager.importAssetsIfNeed("kb") }
    }

    private fun rows(): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().getAll() }

    private fun ftsCount(): Int = runBlocking { db.knowledgeDao().countFts() }

    private fun importedStatus(): String? = runBlocking { db.knowledgeDao().getImportedFileStatus(fileId) }

    private fun assertCommitted(expected: Int) {
        assertEquals("rows", expected, rows().size)
        assertEquals("fts", expected, ftsCount())
        assertEquals("fts == rows", rows().size, ftsCount())
        assertEquals("imported marker", "imported", importedStatus())
    }

    @Test
    fun `valid first import commits rows fts and imported marker`() {
        runImport(ByteArrayInputStream(bytesOf(fullKb(5))))

        assertCommitted(5)
    }

    @Test
    fun `tail failure after two batches rolls back and never marks imported`() {
        runImport(FailingTailInputStream(bytesOf(truncatedKb(flushed = 200))))

        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertNotEquals("must not be imported", "imported", importedStatus())
        assertEquals("recorded as failed for retry", "failed", importedStatus())
        println("STATE tail-failure rows=${rows().size} fts=${ftsCount()} status=${importedStatus()} fileId=$fileId")
    }

    @Test
    fun `cancellation mid import leaves nothing behind and is not marked imported`() {
        var cancelled = false
        try {
            runImport(CancellingTailInputStream(bytesOf(truncatedKb(flushed = 200))))
        } catch (c: CancellationException) {
            cancelled = true
        }

        assertTrue("cancellation must propagate", cancelled)
        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertNull("no imported marker", importedStatus())
        println("STATE cancellation rows=${rows().size} fts=${ftsCount()} status=${importedStatus()} fileId=$fileId")
    }

    @Test
    fun `retry with the same fileId succeeds after a failed attempt`() {
        runImport(FailingTailInputStream(bytesOf(truncatedKb(flushed = 200))))
        assertEquals(0, rows().size)

        runImport(ByteArrayInputStream(bytesOf(fullKb(5))))

        assertCommitted(5)
        println("STATE retry rows=${rows().size} fts=${ftsCount()} status=${importedStatus()} fileId=$fileId")
    }

    @Test
    fun `failed import preserves existing same-fileId rows and unrelated kb`() {
        val sameId = ImportUtils.stableId64("$fileId::e1::t1::1")
        val otherId = ImportUtils.stableId64("other-file::e9::t9::1")
        runBlocking {
            db.knowledgeDao().upsertBatch(
                listOf(
                    KnowledgeEntity(
                        id = sameId,
                        title = "OLD",
                        content = "OLD CONTENT",
                        contentNormalized = "old",
                        searchContent = "old",
                        source = "assets/kb/atomic",
                        category = "old",
                    ),
                    KnowledgeEntity(
                        id = otherId,
                        title = "OTHER",
                        content = "OTHER CONTENT",
                        contentNormalized = "other",
                        searchContent = "other",
                        source = "assets/kb/other",
                        category = "other",
                    ),
                ),
            )
        }

        runImport(FailingTailInputStream(bytesOf(truncatedKb(flushed = 200))))

        val same = runBlocking { db.knowledgeDao().getById(sameId) }
        assertNotNull("existing same-fileId row must survive", same)
        assertEquals("OLD CONTENT", same!!.content)
        assertEquals("assets/kb/atomic", same.source)

        val other = runBlocking { db.knowledgeDao().getById(otherId) }
        assertNotNull("unrelated kb row must survive", other)
        assertEquals("OTHER CONTENT", other!!.content)

        assertEquals("no half-imported rows committed", 2, rows().size)
        println("STATE protection rows=${rows().size} sameContent=${same.content} otherContent=${other.content}")
    }

    @Test
    fun `second batch write failure rolls back and never marks imported`() {
        manager = buildManager(FailingBatchDao(db.knowledgeDao(), failFromFlush = 2))
        runImport(ByteArrayInputStream(bytesOf(fullKb(250))))

        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertEquals("failed", importedStatus())
        println("STATE batch-failure rows=${rows().size} fts=${ftsCount()} status=${importedStatus()}")
    }

    @Test
    fun `fts rebuild failure rolls back and never marks imported`() {
        manager = buildManager(FailingFtsDao(db.knowledgeDao()))
        runImport(ByteArrayInputStream(bytesOf(fullKb(5))))

        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertEquals("failed", importedStatus())
        println("STATE fts-failure rows=${rows().size} fts=${ftsCount()} status=${importedStatus()}")
    }

    @Test
    fun `invalid entry in an object root fails the import instead of silently dropping it`() {
        val kb =
            """{"fileMetadata":{"source":"$declaredSource"},"entries":[""" +
                """${entries(2)},"not-an-entry",${entries(2, from = 3)}]}"""
        runImport(ByteArrayInputStream(bytesOf(kb)))

        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertEquals("failed", importedStatus())
        println("STATE invalid-entry-object rows=${rows().size} status=${importedStatus()}")
    }

    @Test
    fun `invalid entry in an array root fails the import instead of silently dropping it`() {
        val kb = """[${entries(2)},"not-an-entry",${entries(2, from = 3)}]"""
        runImport(ByteArrayInputStream(bytesOf(kb)))

        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertEquals("failed", importedStatus())
        println("STATE invalid-entry-array rows=${rows().size} status=${importedStatus()}")
    }

    @Test
    fun `array root with an unreadable tail rolls back and never marks imported`() {
        val prefix = "[" + entries(200)
        runImport(FailingTailInputStream(bytesOf(prefix)))

        assertEquals("no searchable rows", 0, rows().size)
        assertEquals("no fts rows", 0, ftsCount())
        assertNotEquals("imported", importedStatus())
        println("STATE array-tail rows=${rows().size} fts=${ftsCount()} status=${importedStatus()}")
    }

    @Test
    fun `duplicate entry within a file is skipped while the import still succeeds`() {
        val kb =
            """{"fileMetadata":{"source":"$declaredSource"},"entries":[""" +
                """{"entryId":"dup","jobTitle":"t","position":1,"contentMarkdown":"a"},""" +
                """{"entryId":"dup","jobTitle":"t","position":1,"contentMarkdown":"b"}]}"""
        runImport(ByteArrayInputStream(bytesOf(kb)))

        assertEquals("duplicate skipped", 1, rows().size)
        assertEquals(1, ftsCount())
        assertEquals("imported", importedStatus())
        println("STATE duplicate-skip rows=${rows().size} status=${importedStatus()}")
    }
}
