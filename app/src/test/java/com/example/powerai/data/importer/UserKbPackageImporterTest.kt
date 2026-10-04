package com.example.powerai.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.AppConfig
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.repository.VectorRepository
import com.example.powerai.util.PdfStorage
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Production-chain tests for [UserKbPackageImporter] against a real Room database and a plain
 * directory source: entries/blocks/FTS, resource mirroring, skip/replace semantics, package
 * isolation and rollback on bad JSON / missing shot / traversal / batch / FTS failures.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class UserKbPackageImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var context: android.content.Context
    private lateinit var tempRoot: File
    private lateinit var importer: UserKbPackageImporter

    private val sha = "a".repeat(64)
    private val pdfSource = "pdf:$sha::doc.pdf"

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
        context = ApplicationProvider.getApplicationContext()
        tempRoot = File(System.getProperty("java.io.tmpdir"), "powerai-kb-${UUID.randomUUID()}").apply { mkdirs() }
        importer = UserKbPackageImporter(context, db.knowledgeDao())
    }

    @After
    fun tearDown() {
        db.close()
        tempRoot.deleteRecursively()
    }

    // ---------------------------------------------------------------- happy path

    @Test
    fun `imports entries blocks fts and materializes referenced shots`() {
        val source =
            writeSource(
                kb(pdfSource, entry("e1", "alpha", 1, "shots/a.png"), entry("e2", "beta", 2, "shots/b.png")),
                mapOf("shots/a.png" to byteArrayOf(1, 2, 3), "shots/b.png" to byteArrayOf(4, 5, 6)),
            )
        val pkg = packageIdOf(source)

        val result = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        assertTrue("expected Imported, got $result", result is UserKbImportResult.Imported)
        result as UserKbImportResult.Imported
        assertEquals(2, result.entries)
        assertEquals(2, result.blocks)
        assertEquals(2, result.assets)
        assertNotNull("declared pdf without original must prompt", result.pdf)
        assertEquals(sha, result.pdf?.fileId)

        val rows = rows()
        assertEquals("entries", 2, rows.size)
        assertTrue("rows attributed to the user package", rows.all { it.packageId == pkg })
        assertTrue("declared pdf source preserved", rows.all { it.source == pdfSource })
        assertEquals("fts rows", 2, countFts())

        // Stored image references must be absolute app-private files that exist on disk.
        val storedUris = rows.flatMap { imageRefs(it.contentBlocksJson) }
        assertEquals("two stored refs", 2, storedUris.size)
        val mirrorRoot = File(context.filesDir, AppConfig.USER_KB_MIRROR_DIR)
        storedUris.forEach { uri ->
            assertTrue("rewritten to private file uri: $uri", uri.startsWith("file://"))
            assertTrue("mirror lives under filesDir/kb_user: $uri", uri.contains(AppConfig.USER_KB_MIRROR_DIR))
        }
        val mirroredNames = mirrorNames(mirrorRoot)
        assertTrue("both shots materialized", mirroredNames.containsAll(setOf("a.png", "b.png")))

        // Removing the source directory must not break the saved images.
        assertTrue(source.deleteRecursively())
        assertEquals("mirror survives source removal", mirroredNames, mirrorNames(mirrorRoot))
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    fun `unchanged content is skipped without package deletes`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, "shots/a.png")), mapOf("shots/a.png" to byteArrayOf(9)))
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val before = rows().map { it.id to it.content }

        val counting = CountingDao(db.knowledgeDao())
        val countingImporter = UserKbPackageImporter(context, counting)
        val second = runBlocking { countingImporter.importFromSource(FileKbDirectorySource(source)) }

        assertTrue("unchanged content must skip", second is UserKbImportResult.Skipped)
        assertEquals("no package delete when unchanged", 0, counting.packageDeletes)
        assertEquals("rows unchanged", before, rows().map { it.id to it.content })
    }

    // ---------------------------------------------------------------- replace / isolation

    @Test
    fun `same directory A to B replaces only this package and cleans old mirror`() {
        val sourceA = writeSource(kb(pdfSource, entry("a1", "alpha", 1, "shots/a.png")), mapOf("shots/a.png" to byteArrayOf(1)))
        val sourceB = writeSource(kb(pdfSource, entry("b1", "other", 1, "shots/b.png")), mapOf("shots/b.png" to byteArrayOf(2)))

        runBlocking { importer.importFromSource(FileKbDirectorySource(sourceA)) }
        runBlocking { importer.importFromSource(FileKbDirectorySource(sourceB)) }
        val pkgA = packageIdOf(sourceA)
        val pkgB = packageIdOf(sourceB)
        assertEquals("two independent packages", 2, rows().size)

        // Update directory A to new content: gamma added, alpha removed, same directory identity.
        File(sourceA, KB_JSON_FILE_NAME).writeText(
            kb(pdfSource, entry("a2", "gamma", 1, "shots/c.png"), entry("a3", "delta", 2, null)),
        )
        File(sourceA, "shots/c.png").apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(3))

        val result = runBlocking { importer.importFromSource(FileKbDirectorySource(sourceA)) }
        assertTrue(result is UserKbImportResult.Imported)

        assertEquals("A rows replaced (gamma, delta) + B untouched (other)", 3, rows().size)
        assertTrue("removed alpha gone", rows().none { it.title == "alpha" })
        assertTrue("added gamma present", rows().any { it.title == "gamma" })
        assertTrue("package B untouched", rows().any { it.packageId == pkgB && it.title == "other" })
        assertEquals("A now has 2 rows", 2, rows().count { it.packageId == pkgA })
        assertEquals("A version mirror pruned to one", 1, mirrorDir(pkgA).listFiles()?.size ?: 0)
    }

    @Test
    fun `two packages stay independent`() {
        val sourceA = writeSource(kb(pdfSource, entry("a1", "alpha", 1, null)), emptyMap())
        val sourceB = writeSource(kb(pdfSource, entry("b1", "beta", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(sourceA)) }
        runBlocking { importer.importFromSource(FileKbDirectorySource(sourceB)) }
        assertEquals(1, rows().count { it.packageId == packageIdOf(sourceA) })
        assertEquals(1, rows().count { it.packageId == packageIdOf(sourceB) })
        assertEquals(2, countFts())
    }

    // ---------------------------------------------------------------- failure rollback

    @Test
    fun `bad json keeps the previous package`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val pkg = packageIdOf(source)
        val before = rows().map { it.id to it.content }

        File(source, KB_JSON_FILE_NAME).writeText("""{"fileMetadata":{"source":"$pdfSource"},"entries":[ {"entryId": }""")
        val result = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }

        assertTrue(result is UserKbImportResult.Failed)
        assertEquals("previous package preserved", before, rows().map { it.id to it.content })
        assertEquals(1, rows().count { it.packageId == pkg })
    }

    @Test
    fun `missing shot fails and keeps the previous package`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, "shots/a.png")), mapOf("shots/a.png" to byteArrayOf(1)))
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val before = rows().map { it.id to it.content }

        File(source, KB_JSON_FILE_NAME).writeText(kb(pdfSource, entry("e9", "beta", 1, "shots/missing.png")))
        val result = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }

        assertTrue("missing required resource must fail", result is UserKbImportResult.Failed)
        assertEquals("previous package preserved", before, rows().map { it.id to it.content })
    }

    @Test
    fun `path traversal reference is rejected without writing rows`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, "../../secret.png")), emptyMap())
        val result = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        assertTrue(result is UserKbImportResult.Failed)
        assertEquals(0, rows().size)
    }

    @Test
    fun `batch write failure keeps the previous package`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val before = rows().map { it.id to it.content }

        File(source, KB_JSON_FILE_NAME).writeText(kb(pdfSource, entry("e2", "beta", 1, null)))
        val failing = UserKbPackageImporter(context, FailingBatchDao(db.knowledgeDao()))
        val result = runBlocking { failing.importFromSource(FileKbDirectorySource(source)) }

        assertTrue(result is UserKbImportResult.Failed)
        assertEquals("previous package preserved", before, rows().map { it.id to it.content })
    }

    @Test
    fun `fts rebuild failure keeps the previous package`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val before = rows().map { it.id to it.content }

        File(source, KB_JSON_FILE_NAME).writeText(kb(pdfSource, entry("e2", "beta", 1, null)))
        val failing = UserKbPackageImporter(context, FailingFtsDao(db.knowledgeDao()))
        val result = runBlocking { failing.importFromSource(FileKbDirectorySource(source)) }

        assertTrue(result is UserKbImportResult.Failed)
        assertEquals("previous package preserved", before, rows().map { it.id to it.content })
    }

    @Test
    fun `cancellation keeps the previous package`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val before = rows().map { it.id to it.content }

        File(source, KB_JSON_FILE_NAME).writeText(kb(pdfSource, entry("e2", "beta", 1, null)))
        val cancelling = UserKbPackageImporter(context, CancellingBatchDao(db.knowledgeDao()))

        val cancelled =
            runCatching {
                runBlocking {
                    withTimeout(300) { cancelling.importFromSource(FileKbDirectorySource(source)) }
                }
            }.exceptionOrNull()
        assertTrue("expected cancellation, got $cancelled", cancelled is CancellationException)
        assertEquals("previous package preserved", before, rows().map { it.id to it.content })
    }

    // ---------------------------------------------------------------- pdf prompt

    @Test
    fun `pdf prompt disappears once the matching original is present`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        val first = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) } as UserKbImportResult.Imported
        assertNotNull("prompt while original missing", first.pdf)

        PdfStorage.savePdfBytes(context, byteArrayOf(1, 2, 3), sha)
        File(source, KB_JSON_FILE_NAME).writeText(kb(pdfSource, entry("e2", "beta", 1, null)))
        val second = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) } as UserKbImportResult.Imported
        assertNull("no prompt once original present", second.pdf)
    }

    // ---------------------------------------------------------------- list / remove

    @Test
    fun `list packages reports entry count version time and pdf association`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }

        val summary = runBlocking { importer.listPackages() }.single()

        assertEquals(packageIdOf(source), summary.packageId)
        assertEquals(1, summary.entries)
        assertEquals("doc.pdf", summary.pdfFileName)
        assertTrue("version time recorded", summary.updatedAtMs > 0L)
        assertTrue("status imported", summary.status.equals("imported", ignoreCase = true))
        assertFalse("original PDF not associated yet", summary.pdfAssociated)
    }

    @Test
    fun `remove deletes only this package rows fts marker and mirror`() {
        val sourceA = writeSource(kb(pdfSource, entry("a1", "alpha", 1, "shots/a.png")), mapOf("shots/a.png" to byteArrayOf(1)))
        val sourceB = writeSource(kb(pdfSource, entry("b1", "beta", 1, "shots/b.png")), mapOf("shots/b.png" to byteArrayOf(2)))
        runBlocking { importer.importFromSource(FileKbDirectorySource(sourceA)) }
        runBlocking { importer.importFromSource(FileKbDirectorySource(sourceB)) }
        seedBuiltInAndManualRows()
        val pkgA = packageIdOf(sourceA)
        val pkgB = packageIdOf(sourceB)
        val mirrorA = mirrorDir(pkgA)
        assertTrue("A mirror exists", mirrorA.exists())

        val result = runBlocking { importer.removePackage(pkgA) }

        assertTrue("reported removed: $result", result is UserKbPackageRemovalResult.Removed)
        assertTrue("A rows gone", rows().none { it.packageId == pkgA })
        assertEquals("A marker gone", 0, runBlocking { db.knowledgeDao().importedFileExists(pkgA) })
        assertFalse("A mirror removed", mirrorA.exists())
        assertTrue("search no longer finds removed A", runBlocking { db.knowledgeDao().searchByFts("alpha") }.isEmpty())
        // Nothing else is touched.
        assertEquals("B rows kept", 1, rows().count { it.packageId == pkgB })
        assertTrue("B marker kept", runBlocking { db.knowledgeDao().importedFileExists(pkgB) } > 0)
        assertTrue("built-in row kept", rows().any { it.title == "builtin" })
        assertTrue("manual row kept", rows().any { it.title == "manual" })
        assertEquals("fts matches remaining rows", rows().size, countFts())
    }

    @Test
    fun `remove refuses a non user package and deletes nothing`() {
        seedBuiltInAndManualRows()
        val before = rows().map { it.id to it.title }

        val result = runBlocking { importer.removePackage("builtin-hash") }

        assertTrue("refused: $result", result is UserKbPackageRemovalResult.Failed)
        assertEquals("nothing deleted", before, rows().map { it.id to it.title })
        assertTrue("built-in marker kept", runBlocking { db.knowledgeDao().importedFileExists("builtin-hash") } > 0)
    }

    @Test
    fun `remove invalidates the native vector index`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        val vector = CountingVectorRepository()
        val withVector =
            UserKbPackageImporter(context, db.knowledgeDao(), db.visionCacheDao(), db.embeddingDao(), vector, "vector_index.bin")
        runBlocking { withVector.importFromSource(FileKbDirectorySource(source)) }
        val index = File(context.filesDir, "vector_index.bin")
        index.writeText("stale")

        val result = runBlocking { withVector.removePackage(packageIdOf(source)) }

        assertTrue(result is UserKbPackageRemovalResult.Removed)
        assertEquals("in-memory index cleared", 1, vector.clearCount)
        assertFalse("on-disk index removed", index.exists())
    }

    @Test
    fun `failed removal keeps the package and reports failure`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, null)), emptyMap())
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val pkg = packageIdOf(source)

        val failing = UserKbPackageImporter(context, FailingDeleteDao(db.knowledgeDao()))
        val result = runBlocking { failing.removePackage(pkg) }

        assertTrue("reported failure: $result", result is UserKbPackageRemovalResult.Failed)
        assertEquals("rows kept", 1, rows().count { it.packageId == pkg })
        assertTrue("marker kept", runBlocking { db.knowledgeDao().importedFileExists(pkg) } > 0)
    }

    @Test
    fun `failed update keeps the previous version shots on disk`() {
        val source = writeSource(kb(pdfSource, entry("e1", "alpha", 1, "shots/a.png")), mapOf("shots/a.png" to byteArrayOf(1)))
        runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }
        val pkg = packageIdOf(source)
        assertTrue("old shot on disk before update", mirrorNames(mirrorDir(pkg)).contains("a.png"))

        // The same directory now references a shot that does not exist → the import fails.
        File(source, KB_JSON_FILE_NAME).writeText(kb(pdfSource, entry("e9", "beta", 1, "shots/missing.png")))
        val result = runBlocking { importer.importFromSource(FileKbDirectorySource(source)) }

        assertTrue("reported failure: $result", result is UserKbImportResult.Failed)
        assertTrue("previous version's shot survives", mirrorNames(mirrorDir(pkg)).contains("a.png"))
        assertEquals("rows unchanged", 1, rows().count { it.packageId == pkg })
    }

    // ---------------------------------------------------------------- helpers

    private fun seedBuiltInAndManualRows() =
        runBlocking {
            db.knowledgeDao().upsertBatch(
                listOf(
                    KnowledgeEntity(
                        id = 900L,
                        title = "builtin",
                        content = "builtin body",
                        contentNormalized = "builtin body",
                        searchContent = "builtin body",
                        source = "assets/kb/x",
                        category = "c",
                        packageId = "builtin-hash",
                    ),
                    KnowledgeEntity(
                        id = 901L,
                        title = "manual",
                        content = "manual body",
                        contentNormalized = "manual body",
                        searchContent = "manual body",
                        source = "manual",
                        category = "c",
                        packageId = null,
                    ),
                ),
            )
            db.knowledgeDao().rebuildFts()
            db.knowledgeDao().insertImportedFile(
                ImportedFileEntity("builtin-hash", "builtin", 1L, AssetImportStatus.IMPORTED, "sha-builtin"),
            )
        }

    private fun rows(): List<KnowledgeEntity> = runBlocking { db.knowledgeDao().getAll() }

    private fun countFts(): Int = runBlocking { db.knowledgeDao().countFts() }

    private fun packageIdOf(source: File): String = "user:" + ImportUtils.sha256Hex(FileKbDirectorySource(source).identity)

    private fun mirrorDir(packageId: String): File =
        File(File(context.filesDir, AppConfig.USER_KB_MIRROR_DIR), ImportUtils.sha256Hex(packageId))

    private fun mirrorNames(root: File): Set<String> = root.walkTopDown().filter { it.isFile }.map { it.name }.toSet()

    private fun writeSource(
        json: String,
        shots: Map<String, ByteArray>,
    ): File {
        val dir = File(tempRoot, "src-${UUID.randomUUID()}").apply { mkdirs() }
        File(dir, KB_JSON_FILE_NAME).writeText(json)
        shots.forEach { (relative, bytes) ->
            File(dir, relative).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        return dir
    }

    private fun entry(
        id: String,
        title: String,
        position: Int,
        shot: String?,
    ): String {
        val blocks =
            if (shot == null) {
                "[]"
            } else {
                """[{"id":"b_$id","type":"table","rows":[["h"],["v"]],""" +
                    """"bbox":{"left":1,"top":2,"right":3,"bottom":4},"pageNumber":1,""" +
                    """"imageUri":"$shot","src":"$shot"}]"""
            }
        return """{"entryId":"$id","jobTitle":"$title","position":$position,"pageNumber":1,""" +
            """"contentMarkdown":"$title body","blocks":$blocks}"""
    }

    private fun kb(
        source: String,
        vararg entries: String,
    ): String = """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","source":"$source"},"entries":[${entries.joinToString(",")}]}"""

    private fun imageRefs(contentBlocksJson: String?): List<String> {
        val json = contentBlocksJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val root = JsonParser().parse(json)
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.mapNotNull { element ->
                element.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.get("imageUri")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
            }
        }.getOrDefault(emptyList())
    }

    private class CountingDao(
        private val delegate: KnowledgeDao,
    ) : KnowledgeDao by delegate {
        var packageDeletes = 0

        override suspend fun deleteByPackageId(packageId: String): Int {
            packageDeletes++
            return delegate.deleteByPackageId(packageId)
        }
    }

    private class FailingBatchDao(
        private val delegate: KnowledgeDao,
    ) : KnowledgeDao by delegate {
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
            throw IllegalStateException("batch write failed")
        }
    }

    private class FailingFtsDao(
        private val delegate: KnowledgeDao,
    ) : KnowledgeDao by delegate {
        override suspend fun rebuildFts() {
            throw IllegalStateException("fts rebuild failed")
        }
    }

    private class CancellingBatchDao(
        private val delegate: KnowledgeDao,
    ) : KnowledgeDao by delegate {
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
            delay(Long.MAX_VALUE)
        }
    }

    private class FailingDeleteDao(
        private val delegate: KnowledgeDao,
    ) : KnowledgeDao by delegate {
        override suspend fun deleteByPackageId(packageId: String): Int {
            throw IllegalStateException("delete failed")
        }
    }

    private class CountingVectorRepository : VectorRepository {
        var clearCount = 0
            private set

        override fun init(dim: Int) {}

        override fun upsert(
            ids: LongArray,
            vectors: FloatArray,
        ): Boolean = true

        override fun search(
            query: FloatArray,
            k: Int,
        ): LongArray = LongArray(0)

        override fun saveIndex(path: String): Boolean = true

        override fun loadIndex(path: String): Boolean = true

        override fun clear() {
            clearCount++
        }
    }
}
