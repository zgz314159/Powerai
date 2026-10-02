package com.example.powerai.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.ui.blocks.BlocksParser
import com.example.powerai.ui.screen.pdf.parsePdfBoundingBoxOrNull
import com.example.powerai.util.PdfSourceRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Production asset import uses [StreamingJsonResourceImporter]. The KB-declared
 * source (e.g. a "pdf:{sha256}::{name}" link that enables "在 PDF 中定位") must
 * survive that import instead of being replaced by the asset-path fallback id.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class StreamingImporterSourceContractTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun import(
        kbJson: String,
        fallbackFileId: String = "asset_path_hash",
    ) {
        runBlocking {
            StreamingJsonResourceImporter(db.knowledgeDao()).importFromJson(
                inputStream = ByteArrayInputStream(kbJson.toByteArray(Charsets.UTF_8)),
                batchSize = 16,
                trace = null,
                fallbackFileName = "knowledge_base.json",
                fallbackFileId = fallbackFileId,
            ).collect { }
        }
    }

    private fun singleEntity() = runBlocking { db.knowledgeDao().getAll() }.single()

    @Test
    fun `declared fileMetadata source survives streaming import`() {
        val sha = "a".repeat(64)
        val link = "pdf:$sha::doc.pdf"
        import(
            """
            {
              "fileMetadata": { "schemaVersion": "2.0", "source": "$link" },
              "entries": [
                {
                  "entryId": "e1",
                  "jobTitle": "t",
                  "pageNumber": 1,
                  "blocks": [
                    {"id": "b1", "type": "text", "text": "x", "pageNumber": 1,
                     "bbox": {"left": 1, "top": 2, "right": 3, "bottom": 4}}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val entity = singleEntity()
        assertEquals(link, entity.source)
        assertEquals(sha, PdfSourceRef.parse(entity.source)?.fileId)

        // Entry-level bbox stays parseable and matches the entry page.
        val box = parsePdfBoundingBoxOrNull(entity.bboxJson)
        assertNotNull("entry bbox must be parseable", box)
        assertEquals(1f, box!!.xMin, 0.01f)
        assertEquals(4f, box.yMax, 0.01f)
        val block = BlocksParser.parseBlocks(entity.contentBlocksJson).orEmpty().single()
        assertEquals(1, block.pageNumber)
    }

    @Test
    fun `per entry source wins over the declared source`() {
        val declared = "a".repeat(64)
        val perEntry = "b".repeat(64)
        import(
            """
            {
              "fileMetadata": { "source": "pdf:$declared::a.pdf" },
              "entries": [
                { "entryId": "e1", "source": "pdf:$perEntry::b.pdf", "jobTitle": "t" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("pdf:$perEntry::b.pdf", singleEntity().source)
    }

    @Test
    fun `missing declared source keeps the legacy fallback id`() {
        import(
            """
            [
              { "entryId": "e1", "jobTitle": "t", "contentMarkdown": "hello" }
            ]
            """.trimIndent(),
            fallbackFileId = "asset_path_hash",
        )

        assertEquals("asset_path_hash", singleEntity().source)
    }
}
