package com.example.powerai.data.importer

import android.content.Context
import android.content.res.AssetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.ObservabilityService
import com.example.powerai.core.repository.EmbeddingRepository
import com.example.powerai.core.repository.VectorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Rebuild must invalidate the in-memory native vector index in the **same process**: an id that
 * was searchable before the rebuild must no longer be returned afterwards, without an app restart.
 * A failed or cancelled rebuild (database rolled back) must leave the still-valid index untouched.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class KbRebuildVectorIndexTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var filesDir: File
    private lateinit var manager: DocumentImportManager
    private lateinit var vectorRepo: FakeVectorRepository

    private val assetPath = "kb/atomic/knowledge_base.json"
    private val sha = "e".repeat(64)
    private val declaredSource = "pdf:$sha::doc.pdf"
    private val legacyEntityId = 111L
    private val fileId: String get() = ImportUtils.sha256Hex("asset:$assetPath")

    private val content = linkedMapOf<String, String>()

    private val noEmbedding =
        object : EmbeddingRepository {
            override suspend fun enqueueForEmbedding(items: List<KnowledgeItem>) {}

            override suspend fun storeEmbedding(
                itemId: Long,
                embedding: FloatArray,
            ) {}
        }

    /** In-memory stand-in for the native index, keyed by knowledge entity id. */
    private class FakeVectorRepository : VectorRepository {
        private val store = LinkedHashMap<Long, FloatArray>()
        var clearCount = 0
            private set

        override fun init(dim: Int) {}

        override fun upsert(
            ids: LongArray,
            vectors: FloatArray,
        ): Boolean {
            ids.forEach { id -> store[id] = FloatArray(1) }
            return true
        }

        override fun search(
            query: FloatArray,
            k: Int,
        ): LongArray = store.keys.take(k).toLongArray()

        override fun saveIndex(path: String): Boolean = true

        override fun loadIndex(path: String): Boolean = true

        override fun clear() {
            clearCount++
            store.clear()
        }
    }

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
        filesDir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "kb-index-test")
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
        vectorRepo = FakeVectorRepository()
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
            vectorRepository = vectorRepo,
        )

    private fun entry(
        id: String,
        title: String,
    ): String = """{"entryId":"$id","jobTitle":"$title","position":1,"contentMarkdown":"$title body"}"""

    private fun validKb(): String =
        """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","source":"$declaredSource"},""" +
            """"entries":[${entry("e1", "alpha")},${entry("e2", "beta")}]}"""

    private fun truncatedKb(): String = """{"fileMetadata":{"source":"$declaredSource"},"entries":[${entry("e1", "alpha")}"""

    /** A legacy row attributed to no package, plus a vector indexed under its id. */
    private fun seedLegacyRowAndVector() =
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
            vectorRepo.upsert(longArrayOf(legacyEntityId), FloatArray(1))
        }

    private fun searchIds(): List<Long> = vectorRepo.search(FloatArray(1), 10).toList()

    private fun rebuild() = runBlocking { manager.rebuildBuiltInKnowledgeBase("kb") }

    @Test
    fun `successful rebuild drops the old id from the in-memory index in the same process`() {
        seedLegacyRowAndVector()
        assertEquals("old id searchable before rebuild", listOf(legacyEntityId), searchIds())

        val result = rebuild()

        assertTrue("reported success", result is KbRebuildState.Success)
        assertTrue("in-memory index was cleared", vectorRepo.clearCount >= 1)
        assertTrue("old id no longer searchable after rebuild", searchIds().isEmpty())
        assertTrue("old row gone", runBlocking { db.knowledgeDao().getAll() }.none { it.id == legacyEntityId })
    }

    @Test
    fun `failed rebuild keeps the still-valid index and does not clear it`() {
        seedLegacyRowAndVector()
        whenever(assets.open(assetPath)).thenReturn(FailingTailInputStream(truncatedKb().toByteArray()))

        val result = rebuild()

        assertTrue("reported failure", result is KbRebuildState.Failed)
        assertEquals("index not touched on rollback", 0, vectorRepo.clearCount)
        assertEquals("old id still searchable", listOf(legacyEntityId), searchIds())
    }

    @Test
    fun `cancelled rebuild keeps the still-valid index and does not clear it`() {
        seedLegacyRowAndVector()
        whenever(assets.open(assetPath)).thenReturn(CancellingTailInputStream(truncatedKb().toByteArray()))

        var cancelled = false
        try {
            rebuild()
        } catch (c: CancellationException) {
            cancelled = true
        }

        assertTrue("cancellation propagates", cancelled)
        assertEquals("index not touched on cancellation", 0, vectorRepo.clearCount)
        assertEquals("old id still searchable", listOf(legacyEntityId), searchIds())
    }

    @Test
    fun `disk deletion failure is not reported as a complete success`() {
        seedLegacyRowAndVector()
        // A non-empty directory cannot be removed by File.delete(), forcing a deletion failure.
        val index = File(filesDir, "vector_index.bin")
        index.mkdirs()
        File(index, "child").writeText("x")

        val result = rebuild()

        assertTrue("must not report complete success", result is KbRebuildState.Failed)
    }

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
