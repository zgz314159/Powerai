package com.example.powerai.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.TableBlock
import com.example.powerai.ui.blocks.BlocksParser
import com.example.powerai.ui.screen.pdf.parsePdfBoundingBoxOrNull
import com.example.powerai.ui.screen.pdf.pdfLocateTargetForBlock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

/**
 * End-to-end contract: the committed PaddleModels v2 fixture goes through the real
 * import -> Room persistence -> read back -> BlocksParser chain, and the read-back
 * blocks must drive the detail page "在 PDF 中定位" target.
 *
 * This complements [PaddleModelsTableRowsContractTest] (parser-only) by exercising
 * the persistence boundary instead of the tools in isolation.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class PaddleModelsTableRowsFullChainTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun fixtureStream(): InputStream =
        javaClass.getResourceAsStream(FIXTURE_PATH) ?: error("fixture not found: $FIXTURE_PATH")

    private fun importFixture(): KnowledgeEntity {
        runBlocking {
            // Production import path (DocumentImportManager uses StreamingJsonResourceImporter).
            StreamingJsonResourceImporter(db.knowledgeDao()).importFromJson(
                inputStream = fixtureStream(),
                batchSize = 64,
                trace = null,
                fallbackFileName = "knowledge_base.json",
                fallbackFileId = "sample_doc"
            ).collect { }
        }
        val all = runBlocking { db.knowledgeDao().getAll() }
        assertEquals("fixture must persist exactly one entry", 1, all.size)
        return all.first()
    }

    private fun tablesFrom(entity: KnowledgeEntity): List<TableBlock> {
        assertNotNull("contentBlocksJson must persist", entity.contentBlocksJson)
        return BlocksParser.parseBlocks(entity.contentBlocksJson).orEmpty().filterIsInstance<TableBlock>()
    }

    @Test
    fun `fixture round trips through room into two table blocks`() {
        val entity = importFixture()
        assertEquals(1, entity.pageNumber)
        val tables = tablesFrom(entity)
        assertEquals(2, tables.size)
        assertNotNull(tables.firstOrNull { it.id == "p1_tbl_struct" })
        assertNotNull(tables.firstOrNull { it.id == "p1_tbl_image" })
    }

    @Test
    fun `structured table keeps 3x2 rows empty merged placeholder and five cells with rowSpan 2`() {
        val table = tablesFrom(importFixture()).first { it.id == "p1_tbl_struct" }
        assertEquals(
            listOf(
                listOf("H1", "H2"),
                listOf("merged-span", "b1"),
                listOf("", "b2")
            ),
            table.rows
        )
        assertEquals(3, table.rows.size)
        assertEquals(2, table.rows[0].size)
        assertEquals("", table.rows[2][0])

        val cells = table.cells
        assertNotNull(cells)
        assertEquals(5, cells!!.size)
        val merged = cells.first { it.text == "merged-span" }
        assertEquals(1, merged.row)
        assertEquals(0, merged.col)
        assertEquals(2, merged.rowSpan)
        assertEquals(1, merged.colSpan)
    }

    @Test
    fun `image-only table keeps empty rows no cells and its image uri`() {
        val table = tablesFrom(importFixture()).first { it.id == "p1_tbl_image" }
        assertTrue(table.rows.isEmpty())
        assertTrue(table.cells.isNullOrEmpty())
        assertEquals("shots/p1_tbl_image.png", table.imageUri)
    }

    @Test
    fun `read back block drives a pdf locate target matching the fixture page and bbox`() {
        val entity = importFixture()

        val struct = tablesFrom(entity).first { it.id == "p1_tbl_struct" }
        assertEquals(1, struct.pageNumber)
        val structTarget = pdfLocateTargetForBlock(struct, entity.pageNumber)
        assertNotNull("structured block must expose a PDF locate target", structTarget)
        assertEquals(1, structTarget!!.pageNumber)
        assertEquals(22f, structTarget.boundingBox.xMin, 0.01f)
        assertEquals(206f, structTarget.boundingBox.yMin, 0.01f)
        assertEquals(220f, structTarget.boundingBox.xMax, 0.01f)
        assertEquals(291f, structTarget.boundingBox.yMax, 0.01f)

        val imageOnly = tablesFrom(entity).first { it.id == "p1_tbl_image" }
        val imageTarget = pdfLocateTargetForBlock(imageOnly, entity.pageNumber)
        assertNotNull("image-only block must expose a PDF locate target", imageTarget)
        assertEquals(1, imageTarget!!.pageNumber)
        assertEquals(10f, imageTarget.boundingBox.xMin, 0.01f)
        assertEquals(10f, imageTarget.boundingBox.yMin, 0.01f)
        assertEquals(110f, imageTarget.boundingBox.xMax, 0.01f)
        assertEquals(50f, imageTarget.boundingBox.yMax, 0.01f)
    }

    @Test
    fun `entry level bbox payload stays a single parseable box for the detail header button`() {
        // The detail header "查看 PDF" button consumes entity.bboxJson directly through
        // parsePdfBoundingBoxOrNull; the producer must emit a single parseable box.
        val entity = importFixture()
        val box = parsePdfBoundingBoxOrNull(entity.bboxJson)
        assertNotNull("entity.bboxJson must be parseable by the PDF header button", box)
        assertEquals(22f, box!!.xMin, 0.01f)
        assertEquals(206f, box.yMin, 0.01f)
        assertEquals(220f, box.xMax, 0.01f)
        assertEquals(291f, box.yMax, 0.01f)
    }

    @Test
    fun `historical table_rows and boundingBox inputs are honored through the chain`() {
        val legacy = """
        {
          "entries": [
            {
              "entryId": "legacy_1",
              "unitName": "u",
              "jobTitle": "Legacy",
              "pageNumber": 2,
              "blocks": [
                {
                  "id": "legacy_tbl",
                  "type": "table",
                  "table_rows": [["A", "B"], ["C", "D"]],
                  "boundingBox": {"left": 5, "top": 6, "right": 7, "bottom": 8}
                }
              ]
            }
          ]
        }
        """.trimIndent()

        runBlocking {
            StreamingJsonResourceImporter(db.knowledgeDao()).importFromJson(
                inputStream = legacy.byteInputStream(Charsets.UTF_8),
                batchSize = 16,
                trace = null,
                fallbackFileName = "legacy.json",
                fallbackFileId = "legacy"
            ).collect { }
        }

        val entity = runBlocking { db.knowledgeDao().getAll() }.single()
        val table = BlocksParser.parseBlocks(entity.contentBlocksJson).orEmpty()
            .filterIsInstance<TableBlock>().single()
        assertEquals(listOf(listOf("A", "B"), listOf("C", "D")), table.rows)
        assertEquals("legacy_tbl", table.id)
        // The legacy block carries no page of its own; the entry page is the fallback.
        assertNull(table.pageNumber)
        assertEquals(2, entity.pageNumber)

        val target = pdfLocateTargetForBlock(table, entity.pageNumber)
        assertNotNull("legacy boundingBox must still drive a PDF locate target", target)
        assertEquals(2, target!!.pageNumber)
        assertEquals(5f, target.boundingBox.xMin, 0.01f)
        assertEquals(6f, target.boundingBox.yMin, 0.01f)
        assertEquals(7f, target.boundingBox.xMax, 0.01f)
        assertEquals(8f, target.boundingBox.yMax, 0.01f)
    }

    private companion object {
        const val FIXTURE_PATH = "/contracts/paddlemodels_v2_table_rows_contract.json"
    }
}
