package com.example.powerai.core.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.util.BlocksTextExtractor
import com.example.powerai.core.model.util.TableLabelBlockResolver

/**
 * Re-points a search hit that lands on a table's caption/label block to the table
 * block it labels, so the detail selection and "在 PDF 中定位" reach the table.
 *
 * A table label may sit in the same entry as its table, or in a sibling entry on
 * the same page (the PaddleModels chunker splits a table into its own entry). In the
 * latter case the whole result is promoted to the table's entry.
 *
 * [resolve] optionally takes the text that produced the hit (e.g. the user query or
 * the highlight phrase). When the item carries no block hit yet, that text is used to
 * locate the label block through the same block matcher the rest of the app uses.
 */
internal object KnowledgeTableLabelTargets {
    private data class LabelHit(
        val entity: KnowledgeEntity,
        val blocks: List<TableLabelBlockResolver.BlockInfo>,
        val index: Int,
        val label: TableLabelBlockResolver.BlockInfo,
    )

    suspend fun resolve(
        items: List<KnowledgeItem>,
        dao: KnowledgeDao,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem,
        matchedText: String? = null,
    ): List<KnowledgeItem> {
        if (items.isEmpty()) return items
        return items.map { item ->
            try {
                resolveOne(item, dao, entityToItem, matchedText)
            } catch (_: Throwable) {
                item
            }
        }
    }

    private suspend fun resolveOne(
        item: KnowledgeItem,
        dao: KnowledgeDao,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem,
        matchedText: String?,
    ): KnowledgeItem {
        val hit = prepareLabelHit(item, dao, matchedText) ?: return item
        return sameEntryTarget(hit, item)
            ?: crossEntryTarget(hit, dao, entityToItem)
            ?: item
    }

    private suspend fun prepareLabelHit(
        item: KnowledgeItem,
        dao: KnowledgeDao,
        matchedText: String?,
    ): LabelHit? {
        val entity = dao.getById(item.id)
        val blocks = entity?.let { TableLabelBlockResolver.blockInfos(it.contentBlocksJson) }.orEmpty()
        val index = entity?.let { hitIndexIn(blocks, item, it.contentBlocksJson, matchedText) }
        val label = index?.let { blocks.getOrNull(it) }
        return if (entity == null || index == null || label == null) {
            null
        } else if (TableLabelBlockResolver.isTableLabel(label)) {
            LabelHit(entity, blocks, index, label)
        } else {
            null
        }
    }

    private fun hitIndexIn(
        blocks: List<TableLabelBlockResolver.BlockInfo>,
        item: KnowledgeItem,
        blocksJson: String?,
        matchedText: String?,
    ): Int? {
        val byIndex = item.hitBlockIndex?.takeIf { it in blocks.indices }
        val byId = item.hitBlockId?.let { id -> blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        val byText =
            if (matchedText.isNullOrBlank() || blocksJson.isNullOrBlank()) {
                null
            } else {
                BlocksTextExtractor.findFirstMatchingBlockIndex(blocksJson, matchedText)
            }
        return byIndex ?: byId ?: byText
    }

    private fun sameEntryTarget(
        hit: LabelHit,
        item: KnowledgeItem,
    ): KnowledgeItem? {
        val tableIndex = TableLabelBlockResolver.tableIndexForLabel(hit.blocks, hit.index)
        return tableIndex?.let { item.copy(hitBlockIndex = it, hitBlockId = hit.blocks[it].id) }
    }

    private suspend fun crossEntryTarget(
        hit: LabelHit,
        dao: KnowledgeDao,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem,
    ): KnowledgeItem? {
        val page = hit.label.page ?: return null
        return dao.getByPage(hit.entity.source, page)
            .asSequence()
            .filter { it.id != hit.entity.id }
            .mapNotNull { sibling -> tableCandidateIn(sibling, hit.label, page, entityToItem) }
            .minWithOrNull(CANDIDATE_ORDER)
            ?.item
    }

    private fun tableCandidateIn(
        sibling: KnowledgeEntity,
        label: TableLabelBlockResolver.BlockInfo,
        page: Int,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem,
    ): TableCandidate? {
        val siblingBlocks = TableLabelBlockResolver.blockInfos(sibling.contentBlocksJson)
        val table = TableLabelBlockResolver.nearestTableBelowWithGap(siblingBlocks, label) ?: return null
        val item =
            entityToItem(sibling).copy(
                title = label.text.trim(),
                pageNumber = page,
                hitBlockIndex = table.index,
                hitBlockId = siblingBlocks[table.index].id,
            )
        return TableCandidate(gap = table.gap, siblingId = sibling.id, blockIndex = table.index, item = item)
    }

    private data class TableCandidate(
        val gap: Float,
        val siblingId: Long,
        val blockIndex: Int,
        val item: KnowledgeItem,
    )

    /** Nearest table wins; equal gaps fall back to a deterministic sibling id / block index order. */
    private val CANDIDATE_ORDER: Comparator<TableCandidate> =
        compareBy<TableCandidate> { it.gap }
            .thenBy { it.siblingId }
            .thenBy { it.blockIndex }
}
