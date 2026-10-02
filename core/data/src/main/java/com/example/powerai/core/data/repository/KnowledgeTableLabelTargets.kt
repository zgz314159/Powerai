package com.example.powerai.core.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.util.TableLabelBlockResolver

/**
 * Re-points a search hit that lands on a table's caption/label block to the table
 * block it labels, so the detail selection and "在 PDF 中定位" reach the table.
 *
 * A table label may sit in the same entry as its table, or in a sibling entry on
 * the same page (the PaddleModels chunker splits a table into its own entry). In the
 * latter case the whole result is promoted to the table's entry.
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
    ): List<KnowledgeItem> {
        if (items.isEmpty()) return items
        return items.map { item ->
            try {
                resolveOne(item, dao, entityToItem)
            } catch (_: Throwable) {
                item
            }
        }
    }

    private suspend fun resolveOne(
        item: KnowledgeItem,
        dao: KnowledgeDao,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem,
    ): KnowledgeItem {
        val hit = prepareLabelHit(item, dao) ?: return item
        return sameEntryTarget(hit, item)
            ?: crossEntryTarget(hit, dao, entityToItem)
            ?: item
    }

    private suspend fun prepareLabelHit(
        item: KnowledgeItem,
        dao: KnowledgeDao,
    ): LabelHit? {
        val entity = dao.getById(item.id)
        val blocks = entity?.let { TableLabelBlockResolver.blockInfos(it.contentBlocksJson) }.orEmpty()
        val index = hitIndexIn(blocks, item)
        val label = index?.let { blocks.getOrNull(it) }
        if (entity == null || index == null || label == null) return null
        return if (TableLabelBlockResolver.isTableLabel(label)) LabelHit(entity, blocks, index, label) else null
    }

    private fun hitIndexIn(
        blocks: List<TableLabelBlockResolver.BlockInfo>,
        item: KnowledgeItem,
    ): Int? {
        val byIndex = item.hitBlockIndex?.takeIf { it in blocks.indices }
        return byIndex ?: item.hitBlockId?.let { id -> blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
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
            .mapNotNull { sibling -> tableTargetIn(sibling, hit.label, page, entityToItem) }
            .firstOrNull()
    }

    private fun tableTargetIn(
        sibling: KnowledgeEntity,
        label: TableLabelBlockResolver.BlockInfo,
        page: Int,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem,
    ): KnowledgeItem? {
        val siblingBlocks = TableLabelBlockResolver.blockInfos(sibling.contentBlocksJson)
        val tableIndex = TableLabelBlockResolver.nearestTableBelow(siblingBlocks, label) ?: return null
        return entityToItem(sibling).copy(
            title = label.text.trim(),
            pageNumber = page,
            hitBlockIndex = tableIndex,
            hitBlockId = siblingBlocks[tableIndex].id,
        )
    }
}
