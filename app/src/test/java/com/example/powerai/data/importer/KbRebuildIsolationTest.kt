package com.example.powerai.data.importer

import android.content.Context
import android.content.res.AssetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.AppConfig
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.ImportedFileEntity
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
import org.junit.Assert.assertFalse
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
import java.io.File

/**
 * Regression for the ownership rule that "重建内置知识库" must only touch confirmable built-in
 * packages. A real Room database is seeded with two user packages, a manual import row, an
 * unconfirmable legacy built-in install, and one confirmable built-in package; the isolated
 * rebuild must refresh only the confirmable built-in package and leave everything else untouched.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class KbRebuildIsolationTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var filesDir: File
    private lateinit var manager: DocumentImportManager

    private val builtInAsset = "kb/builtin/knowledge_base.json"
    private val legacyAsset = "kb/legacy/knowledge_base.json"
    private val builtInId: String get() = ImportUtils.sha256Hex("asset:$builtInAsset")
    private val legacyId: String get() = ImportUtils.sha256Hex("asset:$legacyAsset")

    private val userA = "user:" + "a".repeat(64)
    private val userB = "user:" + "b".repeat(64)
    private val pdfSha = "c".repeat(64)
    private val declaredSource = "pdf:$pdfSha::doc.pdf"

    private val content = linkedMapOf<String, String>()
    private val updatedAt = 1_700_000_000_000L
    private val manualTitle = "手工导入的资料"
    private val legacyTitle = "旧版内置残留"

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
        filesDir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "kb-isolation-test")
        filesDir.mkdirs()
        assets = mock()
        context = mock()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(assets.list("kb")).thenReturn(arrayOf("builtin", "legacy"))
        whenever(assets.list("kb/builtin")).thenReturn(arrayOf("knowledge_base.json"))
        whenever(assets.list("kb/legacy")).thenReturn(arrayOf("knowledge_base.json"))
        whenever(assets.list(builtInAsset)).thenReturn(emptyArray<String>())
        whenever(assets.list(legacyAsset)).thenReturn(emptyArray<String>())
        content[builtInAsset] = kb()
        content[legacyAsset] = kb()
        whenever(assets.open(builtInAsset)).thenAnswer {
            ByteArrayInputStream(content.getValue(builtInAsset).toByteArray())
        }
        whenever(assets.open(legacyAsset)).thenAnswer {
            ByteArrayInputStream(content.getValue(legacyAsset).toByteArray())
        }
        manager =
            DocumentImportManager(
                context = context,
                repo = KnowledgeRepositoryImpl(context, db.knowledgeDao(), noEmbedding),
                dao = db.knowledgeDao(),
                scanner = AssetImportScanner(context, db.knowledgeDao()),
                observability = mock<ObservabilityService>(),
                scope = CoroutineScope(Dispatchers.IO + Job()),
                visionCacheDao = db.visionCacheDao(),
                embeddingDao = db.embeddingDao(),
            )
        seedCoexistence()
    }

    @After
    fun tearDown() {
        db.close()
        filesDir.deleteRecursively()
    }

    private fun kb(): String {
        return """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","source":"$declaredSource"},""" +
            """"entries":[${entry("e1", "alpha")},${entry("e2", "beta")}]}"""
    }

    private fun entry(
        id: String,
        title: String,
    ): String = """{"entryId":"$id","jobTitle":"$title","position":1,"contentMarkdown":"$title body"}"""

    private fun row(
        id: Long,
        title: String,
        packageId: String?,
    ): KnowledgeEntity =
        KnowledgeEntity(
            id = id,
            title = title,
            content = "$title body",
            contentNormalized = "$title body",
            searchContent = "$title body",
            source = declaredSource,
            category = "cat",
            packageId = packageId,
        )

    /** Two user packages sharing the same PDF source, a manual row, a legacy install and a built-in package. */
    private fun seedCoexistence() =
        runBlocking {
            val dao = db.knowledgeDao()
            dao.upsertBatch(
                listOf(
                    row(101L, "A-alpha", userA),
                    row(102L, "A-beta", userA),
                    row(201L, "B-gamma", userB),
                    row(301L, manualTitle, null),
                    row(401L, legacyTitle, null),
                    row(501L, "builtin-stale", builtInId),
                ),
            )
            dao.rebuildFts()
            dao.insertImportedFile(ImportedFileEntity(userA, "用户包A", updatedAt, AssetImportStatus.IMPORTED, "sha-a"))
            dao.insertImportedFile(ImportedFileEntity(userB, "用户包B", updatedAt, AssetImportStatus.IMPORTED, "sha-b"))
            // Legacy install: pre-migration marker (no fingerprint) with unattributed rows.
            dao.insertImportedFile(ImportedFileEntity(legacyId, "legacy", updatedAt, AssetImportStatus.IMPORTED, ""))
            // Confirmable built-in package: post-migration marker + attributable rows.
            dao.insertImportedFile(ImportedFileEntity(builtInId, "builtin", updatedAt, AssetImportStatus.IMPORTED, "sha-builtin"))
            userMirror(userA).resolve("v1").mkdirs()
            File(userMirror(userA), "v1/a.png").writeBytes(byteArrayOf(1))
        }

    private fun userMirror(packageId: String): File = File(File(filesDir, AppConfig.USER_KB_MIRROR_DIR), ImportUtils.sha256Hex(packageId))

    private fun rows(): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().getAll() }

    private fun rebuild() = runBlocking { manager.rebuildBuiltInKnowledgeBase("kb") }

    @Test
    fun `isolated rebuild refreshes only the confirmable built-in package`() {
        val result = rebuild()

        assertTrue("reported success: $result", result is KbRebuildState.Success)

        // Confirmable built-in package replaced: stale row gone, fresh KB rows present.
        assertTrue("stale built-in row replaced", rows().none { it.title == "builtin-stale" })
        assertEquals("built-in rows re-imported", 2, rows().count { it.packageId == builtInId })

        // Unconfirmable legacy install preserved (never guessed from source / pdf sha).
        assertTrue("legacy row preserved", rows().any { it.title == legacyTitle })
        assertNotNull("legacy marker preserved", runBlocking { db.knowledgeDao().getImportedFile(legacyId) })

        // Manual import preserved.
        assertTrue("manual row preserved", rows().any { it.title == manualTitle })

        // Both user packages preserved with their markers and private shots.
        assertEquals("user A rows kept", 2, rows().count { it.packageId == userA })
        assertEquals("user B rows kept", 1, rows().count { it.packageId == userB })
        assertNotNull("user A marker kept", runBlocking { db.knowledgeDao().getImportedFile(userA) })
        assertNotNull("user B marker kept", runBlocking { db.knowledgeDao().getImportedFile(userB) })
        assertTrue("user A shot kept", File(userMirror(userA), "v1/a.png").isFile)
    }

    @Test
    fun `clear all removes every package including user manual and legacy`() {
        val result = runBlocking { manager.clearAllKnowledgeBases("kb") }

        assertTrue("reported success: $result", result is KbRebuildState.Success)
        assertTrue("no user rows left", rows().none { it.packageId == userA || it.packageId == userB })
        assertTrue("no manual row left", rows().none { it.title == manualTitle })
        assertTrue("no legacy row left", rows().none { it.title == legacyTitle })
        assertFalse("user A marker gone", runBlocking { db.knowledgeDao().importedFileExists(userA) } > 0)
        assertTrue(
            "legacy asset re-imported with a fresh fingerprint",
            !runBlocking { db.knowledgeDao().getImportedFile(legacyId) }?.contentSha256.isNullOrBlank(),
        )
        // Built-in assets are re-imported so the app stays usable.
        assertEquals("built-in rows rebuilt", 2, rows().count { it.packageId == builtInId })
        assertEquals("legacy asset imported with attribution", 2, rows().count { it.packageId == legacyId })
    }
}
