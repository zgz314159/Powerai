package com.example.powerai.domain.usecase

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.domain.model.PdfFigureItem
import com.example.powerai.ui.blocks.BlocksParser
import javax.inject.Inject

/**
 * PDF figure/table query for the TOC sheet: DAO rows become mapped,
 * de-duplicated and page-sorted [PdfFigureItem] entries. Extracted from
 * PdfFigureListViewModel so the ViewModel only orchestrates state.
 */
class PdfFigureListUseCase
    @Inject
    constructor(
        private val dao: KnowledgeDao,
    ) {
        private companion object {
            private const val SAMPLE_LIMIT = 5000
        }

        @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
        suspend fun loadFigures(fileId: String): List<PdfFigureItem> {
            val sourcePrefix = "assets/kb/${fileId.lowercase()}"
            val rows = dao.sampleBySourcePrefix(sourcePrefix, SAMPLE_LIMIT)
            if (rows.isEmpty()) return emptyList()

            val out = ArrayList<PdfFigureItem>()
            val seenImageUris = HashSet<String>()

            for (entity in rows) {
                val blocks = BlocksParser.parseBlocks(entity.contentBlocksJson) ?: continue
                for (block in blocks) {
                    val (caption, imageUri, isTable) =
                        when (block) {
                            is ImageBlock -> Triple(block.caption?.takeIf { it.isNotBlank() }, block.imageUri ?: block.src, false)
                            is TableBlock -> Triple(null, block.imageUri, true)
                            else -> continue
                        }
                    if (imageUri.isNullOrBlank()) continue
                    if (!seenImageUris.add(imageUri)) continue
                    val page = block.pageNumber ?: entity.pageNumber ?: continue
                    out.add(
                        PdfFigureItem(
                            id = block.id ?: imageUri,
                            caption = caption ?: if (isTable) "表格 · 第 $page 页" else "图 · 第 $page 页",
                            imageUri = imageUri,
                            subfolder = entity.source,
                            pageNumber = page,
                            isTable = isTable,
                        ),
                    )
                }
            }
            return out.sortedBy { it.pageNumber }
        }
    }
