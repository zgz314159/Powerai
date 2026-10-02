package com.example.powerai.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.repository.EmbeddingRepository
import com.example.powerai.data.importer.StreamingJsonResourceImporter
import com.example.powerai.ui.blocks.BlocksParser
import com.example.powerai.ui.screen.main.knowledgeDetailTargetOrNull
import com.example.powerai.ui.screen.pdf.pdfLocateTargetForBlock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: a search hit that lands on a table's caption/label block must resolve
 * (detail block selection + "在 PDF 中定位") to the table it labels, not the caption line.
 *
 * Reproduces the PaddleModels 103号(2) KB shapes:
 *  - query 1: the caption lives in a text entry, the table in a sibling entry on the same page.
 *  - query 2: the caption and the table live in the same entry.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class TableLabelPdfLocateRegressionTest {
    private lateinit var db: AppDatabase

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun import(json: String) {
        runBlocking {
            StreamingJsonResourceImporter(db.knowledgeDao()).importFromJson(
                inputStream = json.byteInputStream(Charsets.UTF_8),
                batchSize = 16,
                trace = null,
                fallbackFileName = "mini.pdf",
                fallbackFileId = "mini",
            ).collect { }
        }
    }

    private fun search(query: String): List<KnowledgeItem> =
        runBlocking {
            KnowledgeRepositoryImpl(
                ApplicationProvider.getApplicationContext(),
                db.knowledgeDao(),
                noEmbedding,
            ).searchLocal(query)
        }

    private fun resolvedBlock(item: KnowledgeItem): Pair<KnowledgeBlock, KnowledgeEntity> {
        val target = knowledgeDetailTargetOrNull(item, "")
        assertNotNull("search item must expose a detail target", target)
        val resolvedTarget = target!!
        val entity = runBlocking { db.knowledgeDao().getById(resolvedTarget.id) }
        assertNotNull("detail entity must exist", entity)
        val resolvedEntity = entity!!
        val blocks = BlocksParser.parseBlocks(resolvedEntity.contentBlocksJson).orEmpty()
        val block =
            resolvedTarget.blockId?.let { id -> blocks.firstOrNull { it.id == id } }
                ?: resolvedTarget.blockIndex?.let { blocks.getOrNull(it) }
        assertNotNull("detail target must resolve to a block", block)
        return block!! to resolvedEntity
    }

    private fun insertEntity(
        id: Long,
        source: String,
        content: String,
        blocksJson: String,
    ) {
        runBlocking {
            db.knowledgeDao().insert(
                KnowledgeEntity(
                    id = id,
                    title = "Page 1",
                    content = content,
                    source = source,
                    contentNormalized = content,
                    searchContent = content,
                    pageNumber = 1,
                    contentBlocksJson = blocksJson,
                ),
            )
        }
    }

    private fun captionBlocksJson(
        id: String,
        text: String,
        top: Float,
        bottom: Float,
    ): String {
        return """[{"id":"$id","type":"code","code":"$text","pageNumber":1,"bbox":{"left":40,"top":$top,"right":202,"bottom":$bottom}}]"""
    }

    private fun tableBlocksJson(
        id: String,
        top: Float,
        bottom: Float,
    ): String {
        return """[{"id":"$id","type":"table","pageNumber":1,"bbox":{"left":22,"top":$top,"right":221,"bottom":$bottom}}]"""
    }

    @Test
    fun `cross entry caption hit resolves to the table on the same page`() {
        import(CROSS_ENTRY_KB)

        val results = search("内燃机带负荷磨合的运转时间表")
        assertTrue("caption query must return a result", results.isNotEmpty())

        val item = results.first()
        val (block, entity) = resolvedBlock(item)
        assertTrue("hit must resolve to the table block, not the caption", block is TableBlock)
        assertEquals("p1_tbl1", block.id)
        assertEquals(1, entity.pageNumber)

        val target = pdfLocateTargetForBlock(block, entity.pageNumber)
        assertNotNull(target)
        assertEquals(1, target!!.pageNumber)
        assertEquals(22f, target.boundingBox.xMin, 0.01f)
        assertEquals(102f, target.boundingBox.yMin, 0.01f)
        assertEquals(221f, target.boundingBox.xMax, 0.01f)
        assertEquals(150f, target.boundingBox.yMax, 0.01f)
    }

    @Test
    fun `same entry caption hit resolves to the sibling table block`() {
        import(SAME_ENTRY_KB)

        val results = search("发电机允许温升表")
        assertTrue(results.isNotEmpty())

        val (block, _) = resolvedBlock(results.first())
        assertTrue(block is TableBlock)
        assertEquals("p2_tbl1", block.id)

        val target = pdfLocateTargetForBlock(block, 2)
        assertNotNull(target)
        assertEquals(2, target!!.pageNumber)
        assertEquals(42f, target.boundingBox.yMin, 0.01f)
        assertEquals(180f, target.boundingBox.yMax, 0.01f)
    }

    @Test
    fun `ordinary body hit is not re-pointed to a table`() {
        import(CROSS_ENTRY_KB)

        val results = search("前面一段说明")
        assertTrue(results.isNotEmpty())

        val (block, _) = resolvedBlock(results.first())
        assertTrue("body hit must stay in the text entry", block !is TableBlock)
        assertEquals("p1_b1", block.id)
    }

    @Test
    fun `cross entry caption picks the nearest table regardless of sibling id order`() {
        val source = "verify-nearest"
        val caption = "内燃机带负荷磨合的运转时间表 表1"
        insertEntity(
            id = 900L,
            source = source,
            content = caption,
            blocksJson = captionBlocksJson("cap_near", caption, top = 90f, bottom = 100f),
        )
        // The farther table has the smaller id, so dao.getByPage (ORDER BY id) yields it first.
        insertEntity(id = 100L, source = source, content = "顺号 负荷", blocksJson = tableBlocksJson("far_tbl", top = 130f, bottom = 160f))
        insertEntity(id = 200L, source = source, content = "顺号 负荷", blocksJson = tableBlocksJson("near_tbl", top = 104f, bottom = 150f))

        val results = search("内燃机带负荷磨合的运转时间表")
        assertTrue(results.isNotEmpty())

        val (block, entity) = resolvedBlock(results.first())
        assertTrue("expected the adjacent table, not the farther one", block is TableBlock)
        assertEquals("near_tbl", block.id)
        assertEquals(200L, entity.id)
    }

    @Test
    fun `cross entry equal distance resolves deterministically`() {
        val source = "verify-tie"
        val caption = "发电机允许温升表 表2"
        insertEntity(
            id = 900L,
            source = source,
            content = caption,
            blocksJson = captionBlocksJson("cap_tie", caption, top = 90f, bottom = 100f),
        )
        insertEntity(id = 300L, source = source, content = "顺号 部件", blocksJson = tableBlocksJson("tie_b", top = 110f, bottom = 140f))
        insertEntity(id = 150L, source = source, content = "顺号 部件", blocksJson = tableBlocksJson("tie_a", top = 110f, bottom = 140f))

        val results = search("发电机允许温升表")
        assertTrue(results.isNotEmpty())

        val (block, entity) = resolvedBlock(results.first())
        assertTrue(block is TableBlock)
        // Equal gaps: deterministic tie-break by sibling id (150 < 300).
        assertEquals("tie_a", block.id)
        assertEquals(150L, entity.id)
    }

    private companion object {
        const val CROSS_ENTRY_KB = """
        {
          "fileMetadata": { "fileId": "mini", "fileName": "mini.pdf", "source": "mini" },
          "entries": [
            {
              "entryId": "mini_p1_text", "jobTitle": "迷你条款", "pageNumber": 1, "position": 1, "kind": "text",
              "contentMarkdown": "前面一段说明。\n\n内燃机带负荷磨合的运转时间表 表1",
              "contentNormalized": "前面一段说明。 内燃机带负荷磨合的运转时间表 表1",
              "blocks": [
                { "id": "p1_b1", "type": "code", "code": "前面一段说明。", "pageNumber": 1, "semanticRole": "body", "searchable": true,
                  "bbox": { "left": 22, "top": 50, "right": 222, "bottom": 60 } },
                { "id": "p1_b2", "type": "code", "code": "内燃机带负荷磨合的运转时间表 表1", "pageNumber": 1, "semanticRole": "body", "searchable": true,
                  "bbox": { "left": 40, "top": 90, "right": 202, "bottom": 100 } }
              ]
            },
            {
              "entryId": "mini_p1_tbl1", "jobTitle": "Page 1", "pageNumber": 1, "position": 2, "kind": "table",
              "contentMarkdown": "| 顺号 | 负荷 |\n| --- | --- |\n| 1 | 25 |",
              "contentNormalized": "顺号 负荷 1 25",
              "blocks": [
                { "id": "p1_tbl1", "type": "table", "rows": [["顺号", "负荷"], ["1", "25"]], "pageNumber": 1, "semanticRole": "table", "searchable": true,
                  "bbox": { "left": 22, "top": 102, "right": 221, "bottom": 150 }, "imageUri": "shots/p1_tbl1.png", "src": "shots/p1_tbl1.png" }
              ]
            }
          ]
        }
        """

        const val SAME_ENTRY_KB = """
        {
          "fileMetadata": { "fileId": "mini2", "fileName": "mini2.pdf", "source": "mini2" },
          "entries": [
            {
              "entryId": "mini2_p2_tbl1", "jobTitle": "Page 2", "pageNumber": 2, "position": 1, "kind": "table",
              "contentMarkdown": "| 顺号 | 部件 |\n| --- | --- |\n| 1 | 绕组 |",
              "contentNormalized": "顺号 部件 1 绕组",
              "blocks": [
                { "id": "p2_b1", "type": "code", "code": "发电机允许温升表 表2", "pageNumber": 2, "semanticRole": "figure_callout", "searchable": false,
                  "bbox": { "left": 74, "top": 30, "right": 197, "bottom": 40 } },
                { "id": "p2_tbl1", "type": "table", "rows": [["顺号", "部件"], ["1", "绕组"]], "pageNumber": 2, "semanticRole": "table", "searchable": true,
                  "bbox": { "left": 22, "top": 42, "right": 212, "bottom": 180 }, "imageUri": "shots/p2_tbl1.png", "src": "shots/p2_tbl1.png" }
              ]
            }
          ]
        }
        """
    }
}
