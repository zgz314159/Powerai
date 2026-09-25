package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

internal data class KnowledgeDetailTarget(
    val id: Long,
    val blockIndex: Int?,
    val blockId: String?,
    val detailHighlight: String?
)

internal fun knowledgeDetailTargetOrNull(
    item: KnowledgeItem?,
    fallbackHighlight: String
): KnowledgeDetailTarget? {
    if (item == null || item.id <= 0L) return null
    val detailHighlight = item.highlightHint?.takeIf { it.isNotBlank() }
        ?: fallbackHighlight.takeIf { it.isNotBlank() }
    return KnowledgeDetailTarget(
        id = item.id,
        blockIndex = item.hitBlockIndex,
        blockId = item.hitBlockId,
        detailHighlight = detailHighlight
    )
}

internal fun citationDetailTargetOrNull(
    citationNumber: Int,
    evidenceItems: List<KnowledgeItem>,
    fallbackHighlight: String
): KnowledgeDetailTarget? {
    if (citationNumber <= 0) return null
    return knowledgeDetailTargetOrNull(
        item = evidenceItems.getOrNull(citationNumber - 1),
        fallbackHighlight = fallbackHighlight
    )
}
