package com.example.powerai.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.AppConfig
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Local acceptance only: imports a **repository-external** PaddleModels output directory (with its
 * `knowledge_base.json` and referenced `shots/`) through the production user-directory import chain
 * and asserts the declared entries / blocks / FTS, that every referenced shot is materialized into
 * app-private storage, and that the two acceptance tables keep their page/bbox. Skipped unless
 * `POWERAI_USER_KB_DIR` points at the directory, so no external sample is committed or required by CI.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class UserKbDirectoryAcceptanceTest {
    @Test
    fun `external user kb directory reads back exactly as declared`() {
        val path = System.getenv("POWERAI_USER_KB_DIR")?.takeIf { it.isNotBlank() }
        assumeTrue("POWERAI_USER_KB_DIR not set; skipping user-dir acceptance", path != null)
        val directory = File(path!!)
        assumeTrue("external KB dir not found: $directory", File(directory, KB_JSON_FILE_NAME).isFile)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            val dao = db.knowledgeDao()
            val importer = UserKbPackageImporter(context, dao)
            val result = runBlocking { importer.importFromSource(FileKbDirectorySource(directory)) }

            assertTrue("expected Imported, got $result", result is UserKbImportResult.Imported)
            result as UserKbImportResult.Imported
            assertEquals("entries", 48, result.entries)
            assertEquals("blocks", 839, result.blocks)
            assertEquals("materialized assets", 42, result.assets)

            val rows = runBlocking { dao.getAll() }
            assertEquals("entries", 48, rows.size)
            assertEquals("fts rows", 48, runBlocking { dao.countFts() })
            assertTrue("declared pdf source preserved", rows.all { it.source.startsWith("pdf:06c857b0") })

            val assets = rows.flatMap { imageRefs(it.contentBlocksJson) }.distinct()
            assertEquals("distinct referenced assets", 42, assets.size)
            val mirrorRoot = File(context.filesDir, AppConfig.USER_KB_MIRROR_DIR)
            val mirroredNames = mirrorRoot.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
            assertEquals("mirrored asset files", 42, mirroredNames.size)
            assets.forEach { uri ->
                assertTrue("asset uri under kb_user: $uri", uri.contains(AppConfig.USER_KB_MIRROR_DIR))
                val decoded = java.net.URLDecoder.decode(uri, "UTF-8").replace('\\', '/')
                val name = decoded.substringAfterLast('/')
                assertTrue("asset mirrored: $name", mirroredNames.contains(name))
            }

            assertTable(rows, "p29_tbl1", page = 29, left = 22, top = 206, right = 221, bottom = 292)
            assertTable(rows, "p32_tbl1", page = 32, left = 22, top = 45, right = 212, bottom = 148)
        } finally {
            db.close()
        }
    }

    private fun assertTable(
        rows: List<KnowledgeEntity>,
        blockId: String,
        page: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val block =
            rows.asSequence()
                .flatMap { blocksOf(it.contentBlocksJson).asSequence() }
                .firstOrNull { it.get("id")?.asString == blockId }
        assertTrue("table block $blockId present", block != null)
        val obj = block!!
        val bbox = obj.get("bbox")?.takeIf { it.isJsonObject }?.asJsonObject
        assertEquals("$blockId pageNumber", page, obj.get("pageNumber")?.asInt)
        assertEquals("$blockId bbox.left", left, bbox?.get("left")?.asInt)
        assertEquals("$blockId bbox.top", top, bbox?.get("top")?.asInt)
        assertEquals("$blockId bbox.right", right, bbox?.get("right")?.asInt)
        assertEquals("$blockId bbox.bottom", bottom, bbox?.get("bottom")?.asInt)
    }

    private fun blocksOf(contentBlocksJson: String?): List<JsonObject> {
        val json = contentBlocksJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val root = JsonParser().parse(json)
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
        }.getOrDefault(emptyList())
    }

    private fun imageRefs(contentBlocksJson: String?): List<String> {
        val json = contentBlocksJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val root = JsonParser().parse(json)
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.flatMap { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@flatMap emptyList()
                val uris = mutableListOf<String>()
                obj.get("imageUri")?.takeIf { it.isJsonPrimitive }?.asString?.let { uris.add(it) }
                obj.get("src")?.takeIf { it.isJsonPrimitive }?.asString?.let { uris.add(it) }
                uris
            }
        }.getOrDefault(emptyList())
    }
}
