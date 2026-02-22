package com.example.powerai.ui.screen.detail

import com.example.powerai.ui.blocks.BlocksParser
import com.example.powerai.ui.blocks.BlocksUtils

internal fun buildBlocksComputed(contentBlocksJson: String, highlight: String): BlocksComputed {
    val parsed = BlocksParser.parseBlocks(contentBlocksJson).orEmpty()
    val normalizedTexts = parsed.map { BlocksUtils.normalizedPlainText(it) }
    val matches =
        if (highlight.isBlank()) emptyList() else BlocksUtils.matchIndicesNormalized(normalizedTexts, highlight)
    return BlocksComputed(parsed, normalizedTexts, matches)
}
