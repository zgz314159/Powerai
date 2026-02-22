package com.example.powerai.ui.screen.detail

import com.example.powerai.ui.blocks.KnowledgeBlock
import io.noties.markwon.Markwon

internal data class MarkwonRenderState(
    var lastMarkdown: String = "",
    var useNoTables: Boolean = false,
    var noTablesMarkwon: Markwon? = null,
    // Markwon ext-tables can throw ArithmeticException("divide by zero") when the first draw
    // happens with a transient 0-width (SpanUtils.width -> 0). Retry a few frames before
    // permanently disabling tables.
    var tableRowSpanRetryCount: Int = 0,
    // If we temporarily downgraded to no-tables due to a draw crash, try one re-enable attempt
    // after layout stabilizes.
    var scheduledReenableTables: Boolean = false,
    var reenableTablesAttempts: Int = 0
)

internal data class BlocksComputed(
    val blocks: List<KnowledgeBlock>,
    val normalizedBlockTexts: List<String>,
    val matchIndices: List<Int>
)
