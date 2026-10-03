package com.example.powerai.ui.screen.main

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.repository.AnnRetriever
import com.example.powerai.core.repository.EmbeddingRepository
import com.example.powerai.data.repository.RoomFtsRetriever
import com.example.powerai.domain.retrieval.HybridRetrievalService
import com.example.powerai.domain.usecase.AskAiUseCase
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.feature.searchchat.RetrievalFusionUseCase
import com.example.powerai.ui.blocks.BlocksParser
import com.example.powerai.ui.screen.pdf.pdfLocateTargetForBlock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for the two real "本地" search failures on the full 158-page KB:
 *
 *  1. `内燃机带负荷磨合的运转时间表` — the caption sits in a page-28 text entry while the
 *     table lives in a sibling page-29 entry; the result must resolve to the table block.
 *  2. `发电机允许温升表` — the caption is a block inside the table entry but is absent from
 *     the markdown preview, which used to make the evidence refiner discard the hit (0 UI
 *     results). It must surface and resolve to the table block.
 *
 * Unlike [com.example.powerai.data.repository.TableLabelPdfLocateRegressionTest] (which isolates
 * the production FTS retriever + [KnowledgeRepository.resolveTableLabelTargets] resolver), this
 * test drives the exact use case the "本地" page uses ([HybridQueryUseCase.localMode]) and then
 * walks the final detail / PDF locate resolution.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class LocalModeTableEvidenceRegressionTest {
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

    private fun localModeUseCase(): HybridQueryUseCase {
        val dao = db.knowledgeDao()
        val repo =
            KnowledgeRepositoryImpl(
                ApplicationProvider.getApplicationContext(),
                dao,
                noEmbedding,
            )
        val ann =
            object : AnnRetriever {
                override suspend fun search(
                    query: String,
                    k: Int,
                ): List<RetrievalResult> = emptyList()
            }
        val hybrid =
            HybridRetrievalService(
                ann,
                RoomFtsRetriever(dao),
                60,
                mapOf("fts" to 2.0, "ann" to 1.0, "native" to 1.0),
                0.06,
            )
        val sparse = mock<SparseSearcher>()
        whenever(sparse.search(any(), any<Int>())).thenReturn(emptyList())
        return HybridQueryUseCase(
            RetrievalFusionUseCase(hybrid, sparse),
            mock<AskAiUseCase>(),
            mock<GemmaLocalInference>(),
            repo,
        )
    }

    private fun runLocal(query: String): HybridQueryUseCase.LocalModeResult =
        runBlocking { localModeUseCase().localMode(query, query, this) {} }

    /** Resolves the top evidence item to its detail block + PDF locate bounding box. */
    private fun resolveTopBlock(item: KnowledgeItem): Pair<String, com.example.powerai.ui.screen.pdf.PdfLocateTarget> {
        val resolved = resolvedBlock(item) ?: error("detail target must resolve to a block")
        val locate = pdfLocateTargetForBlock(resolved.block, resolved.entity.pageNumber)
        assertNotNull("block must carry a PDF locate bbox", locate)
        return resolved.block.id.orEmpty() to locate!!
    }

    /** Resolves the (optional) detail block an item points at, if it has a block target. */
    private fun resolvedBlock(item: KnowledgeItem): ResolvedBlock? {
        val target = knowledgeDetailTargetOrNull(item, "") ?: return null
        val entity = runBlocking { db.knowledgeDao().getById(target.id) } ?: return null
        val blocks = BlocksParser.parseBlocks(entity.contentBlocksJson).orEmpty()
        val block =
            target.blockId?.let { id -> blocks.firstOrNull { it.id == id } }
                ?: target.blockIndex?.let { blocks.getOrNull(it) }
                ?: return null
        return ResolvedBlock(entity, block)
    }

    private data class ResolvedBlock(
        val entity: KnowledgeEntity,
        val block: com.example.powerai.core.model.KnowledgeBlock,
    )

    @Test
    fun `cross entry caption resolves to the sibling table on the same page`() {
        import(CROSS_ENTRY_KB)

        val outcome = runLocal("内燃机带负荷磨合的运转时间表")
        val item = outcome.query.items.firstOrNull()
        assertNotNull("query must surface an evidence item through local mode", item)

        val (blockId, locate) = resolveTopBlock(item!!)
        assertEquals("p29_tbl1", blockId)
        assertEquals(29, locate.pageNumber)
        assertEquals(22f, locate.boundingBox.xMin, 0.01f)
        assertEquals(206f, locate.boundingBox.yMin, 0.01f)
        assertEquals(221f, locate.boundingBox.xMax, 0.01f)
        assertEquals(292f, locate.boundingBox.yMax, 0.01f)
    }

    @Test
    fun `same entry caption is kept and resolves to the table block`() {
        import(SAME_ENTRY_KB)

        val outcome = runLocal("发电机允许温升表")
        // Root cause of the UI 0-result bug: the caption only lives in the blocks payload,
        // not the markdown preview, and the evidence refiner used to drop the hit.
        assertTrue(
            "caption hit must survive local evidence refinement",
            outcome.query.retrievals.isNotEmpty(),
        )
        assertTrue(outcome.query.totalCandidateCount >= 1)

        val (blockId, locate) = resolveTopBlock(outcome.query.items.first())
        assertEquals("p32_tbl1", blockId)
        assertEquals(32, locate.pageNumber)
        assertEquals(22f, locate.boundingBox.xMin, 0.01f)
        assertEquals(45f, locate.boundingBox.yMin, 0.01f)
        assertEquals(212f, locate.boundingBox.xMax, 0.01f)
        assertEquals(148f, locate.boundingBox.yMax, 0.01f)
    }

    @Test
    fun `ordinary body hit is not re-pointed to a table`() {
        import(CROSS_ENTRY_KB)

        val outcome = runLocal("前面一段说明")
        val item = outcome.query.items.firstOrNull()
        assertNotNull("body query must surface an evidence item", item)

        // The text entry has no table at all, so a body hit must never resolve into a table.
        val resolved = resolvedBlock(item!!)
        if (resolved != null) {
            assertTrue(
                "body hit must not resolve to a table block",
                resolved.block !is TableBlock,
            )
            assertTrue(
                "body hit must stay inside the text entry",
                BlocksParser.parseBlocks(resolved.entity.contentBlocksJson).orEmpty()
                    .none { it is TableBlock },
            )
        }
    }

    @Test
    fun `evidence item is still present when the query only matches the blocks payload`() {
        import(SAME_ENTRY_KB)

        val outcome = runLocal("发电机允许温升表")
        val item = outcome.query.items.first()
        // Root cause: the markdown preview is a table without the caption; the caption only
        // exists as a code block, which is what the retriever matched.
        assertTrue(item.contentBlocksJson.orEmpty().contains("发电机允许温升表"))
        assertTrue(!item.content.contains("发电机允许温升表"))
    }

    @Test
    fun `detail entity page matches the table entry page`() {
        import(SAME_ENTRY_KB)

        val outcome = runLocal("发电机允许温升表")
        val item = outcome.query.items.first()
        val entity = runBlocking { db.knowledgeDao().getById(item.id) }
        assertNotNull(entity)
        assertEquals(32, entity!!.pageNumber)
    }

    private companion object {
        const val CROSS_ENTRY_KB = """
        {
          "fileMetadata": { "fileId": "mini29", "fileName": "mini29.pdf", "source": "mini29" },
          "entries": [
            {
              "entryId": "mini29_p28_text", "jobTitle": "一、内燃机", "pageNumber": 28, "position": 1, "kind": "text",
              "contentMarkdown": "前面一段说明。\n\n内燃机带负荷磨合的运转时间表 表1",
              "contentNormalized": "前面一段说明。 内燃机带负荷磨合的运转时间表 表1",
              "blocks": [
                { "id": "p28_b1", "type": "code", "code": "前面一段说明。", "pageNumber": 28, "semanticRole": "body", "searchable": true,
                  "bbox": { "left": 22, "top": 50, "right": 222, "bottom": 60 } },
                { "id": "p29_b5", "type": "code", "code": "内燃机带负荷磨合的运转时间表 表1", "pageNumber": 29, "semanticRole": "body", "searchable": true,
                  "bbox": { "left": 40, "top": 194, "right": 202, "bottom": 203 } }
              ]
            },
            {
              "entryId": "mini29_p29_tbl1", "jobTitle": "Page 29", "pageNumber": 29, "position": 2, "kind": "table",
              "contentMarkdown": "| 顺号 | 负荷占额定容量的% |\n| --- | --- |\n| 1 | 25 |",
              "contentNormalized": "顺号 负荷 1 25",
              "blocks": [
                { "id": "p29_tbl1", "type": "table", "rows": [["顺号", "负荷"], ["1", "25"]], "pageNumber": 29, "semanticRole": "table", "searchable": true,
                  "bbox": { "left": 22, "top": 206, "right": 221, "bottom": 292 }, "imageUri": "shots/p29_tbl1.png", "src": "shots/p29_tbl1.png" }
              ]
            }
          ]
        }
        """

        const val SAME_ENTRY_KB = """
        {
          "fileMetadata": { "fileId": "mini32", "fileName": "mini32.pdf", "source": "mini32" },
          "entries": [
            {
              "entryId": "mini32_p32_tbl1", "jobTitle": "Page 32", "pageNumber": 32, "position": 1, "kind": "table",
              "contentMarkdown": "| 顺号 | 部件 |\n| --- | --- |\n| 1 | 绕组 |",
              "contentNormalized": "顺号 部件 1 绕组",
              "blocks": [
                { "id": "p32_b1", "type": "code", "code": "发电机允许温升表 表2", "pageNumber": 32, "semanticRole": "figure_callout", "searchable": false,
                  "bbox": { "left": 74, "top": 33, "right": 197, "bottom": 43 } },
                { "id": "p32_tbl1", "type": "table", "rows": [["顺号", "部件"], ["1", "绕组"]], "pageNumber": 32, "semanticRole": "table", "searchable": true,
                  "bbox": { "left": 22, "top": 45, "right": 212, "bottom": 148 }, "imageUri": "shots/p32_tbl1.png", "src": "shots/p32_tbl1.png" }
              ]
            }
          ]
        }
        """
    }
}
