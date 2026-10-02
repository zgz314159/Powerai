package com.example.powerai.ui.screen.pdf

import com.example.powerai.core.model.KnowledgeBlock

/**
 * Minimal PDF locate target for a single content block.
 *
 * Bridges the detail "在 PDF 中定位" flow (block page number + block bbox) to the
 * [PdfViewerScreen] highlight contract, reusing [parsePdfBoundingBoxOrNull] so
 * there is exactly one bbox interpretation in the app.
 */
internal data class PdfLocateTarget(
    val pageNumber: Int?,
    val bboxJson: String,
    val boundingBox: PdfBoundingBox
)

/**
 * Builds the PDF locate target for [block], falling back to [fallbackPageNumber]
 * when the block has no page of its own. Returns null when the block carries no
 * parseable bbox (nothing to locate).
 */
internal fun pdfLocateTargetForBlock(
    block: KnowledgeBlock,
    fallbackPageNumber: Int? = null
): PdfLocateTarget? {
    val bboxJson = block.boundingBox?.takeIf { it.isNotBlank() }
    val boundingBox = bboxJson?.let { parsePdfBoundingBoxOrNull(it) }
    return if (bboxJson != null && boundingBox != null) {
        PdfLocateTarget(
            pageNumber = block.pageNumber ?: fallbackPageNumber,
            bboxJson = bboxJson,
            boundingBox = boundingBox
        )
    } else {
        null
    }
}
