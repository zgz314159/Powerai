package com.example.powerai.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
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
import java.io.FileInputStream

/**
 * Local acceptance only: reads a **repository-external** real KB through the production importer
 * and asserts the same entries / blocks / FTS the shipped KB declares. Skipped unless
 * `POWERAI_KB_FILE` points at the file, so no external sample is committed or required by CI.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class ExternalKbReadBackTest {
    @Test
    fun `external 158-page KB reads back with the same entries blocks and FTS`() {
        val path = System.getenv("POWERAI_KB_FILE")?.takeIf { it.isNotBlank() }
        assumeTrue("POWERAI_KB_FILE not set; skipping external KB acceptance", path != null)
        val file = File(path!!)
        assumeTrue("external KB not found: $file", file.isFile)

        val db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
        try {
            val dao = db.knowledgeDao()
            runBlocking {
                FileInputStream(file).use { input ->
                    StreamingJsonResourceImporter(dao)
                        .importFromJson(
                            inputStream = input,
                            batchSize = 100,
                            fallbackFileName = file.name,
                            fallbackFileId = "external-kb",
                        ).collect { }
                }
            }

            val rows = runBlocking { dao.getAll() }
            val blockCount = rows.sumOf { countBlocks(it.contentBlocksJson) }
            val fts = runBlocking { dao.countFts() }
            println("EXTERNAL_KB entries=${rows.size} blocks=$blockCount fts=$fts")

            assertEquals("entries", 48, rows.size)
            assertEquals("blocks", 839, blockCount)
            assertEquals("fts rows", 48, fts)
            assertTrue("all rows attributed to the asset package", rows.all { it.packageId == "external-kb" })
            assertTrue("PDF source preserved", rows.any { it.source.startsWith("pdf:") })

            val tablePages = rows.flatMap { tablesOnPage(it.contentBlocksJson) }.toSet()
            assertTrue("page 29 table retained", 29 in tablePages)
            assertTrue("page 32 table retained", 32 in tablePages)
        } finally {
            db.close()
        }
    }

    private fun countBlocks(contentBlocksJson: String?): Int {
        val json = contentBlocksJson?.takeIf { it.isNotBlank() } ?: return 0
        return runCatching {
            val root = JsonParser().parse(json)
            if (root.isJsonArray) root.asJsonArray.size() else 1
        }.getOrDefault(0)
    }

    /** Page numbers of table blocks whose grid is non-empty and carries a bbox. */
    private fun tablesOnPage(contentBlocksJson: String?): List<Int> {
        val json = contentBlocksJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val root = JsonParser().parse(json)
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.mapNotNull { el ->
                val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val type = obj.get("type")?.asString
                val rows = obj.get("rows")
                val hasRows = rows != null && rows.isJsonArray && rows.asJsonArray.size() > 0
                if (type != "table" || !hasRows || !obj.has("bbox")) return@mapNotNull null
                obj.get("pageNumber")?.asInt
            }
        }.getOrDefault(emptyList())
    }
}
