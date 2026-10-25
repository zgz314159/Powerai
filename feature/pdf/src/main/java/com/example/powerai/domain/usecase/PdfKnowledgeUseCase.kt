package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.ui.blocks.BlocksParser
import javax.inject.Inject

/** Page-scoped knowledge snapshot used by the PDF knowledge overlay. */
data class PdfPageKnowledge(
    val knowledgeCount: Int,
    val pageItems: List<KnowledgeItem>,
    val pageBlocks: List<Pair<Long, KnowledgeBlock>>,
)

/**
 * PDF page knowledge query: count, items and hotspot blocks for one
 * (fileId, page) pair, extracted from PdfKnowledgeViewModel.
 */
class PdfKnowledgeUseCase
    @Inject
    constructor(
        private val repository: KnowledgeRepository,
    ) {
        suspend fun loadPage(
            fileId: String,
            page: Int,
        ): PdfPageKnowledge {
            val count = repository.countKnowledgeByPage(fileId, page)
            if (count <= 0) {
                return PdfPageKnowledge(knowledgeCount = 0, pageItems = emptyList(), pageBlocks = emptyList())
            }
            val items = repository.getItemsByPage(fileId, page)
            val pageBlocks =
                items.flatMap { item ->
                    BlocksParser.parseBlocks(item.contentBlocksJson).orEmpty().map { item.id to it }
                }
            return PdfPageKnowledge(knowledgeCount = count, pageItems = items, pageBlocks = pageBlocks)
        }
    }
