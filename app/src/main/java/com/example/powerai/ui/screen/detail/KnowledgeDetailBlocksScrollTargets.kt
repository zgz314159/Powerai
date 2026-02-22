package com.example.powerai.ui.screen.detail

import com.example.powerai.ui.blocks.KnowledgeBlock

internal object KnowledgeDetailBlocksScrollTargets {
    fun initialScrollIndex(
        blocks: List<KnowledgeBlock>,
        initialBlockIndex: Int?,
        initialBlockId: String?
    ): Int? {
        val byId = initialBlockId
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } }

        return byId ?: initialBlockIndex?.takeIf { it >= 0 }
    }
}
