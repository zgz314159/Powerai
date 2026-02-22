package com.example.powerai.ui.screen.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.util.PdfSourceRef

@Composable
internal fun KnowledgeDetailSuccessContent(
    navController: NavHostController,
    entity: KnowledgeEntity,
    rawHighlight: String,
    initialBlockIndex: Int?,
    initialBlockId: String?,
    deepLogicValidationEnabled: Boolean,
    onToggleDeepLogicValidation: (Boolean) -> Unit,
    visionMarkdownByBlockId: Map<String, String>,
    visionBoostingBlockId: String?,
    visionBoostErrorBlockId: String?,
    visionBoostErrorMessage: String?,
    onRequestVisionBoost: (entityId: Long, blockId: String, imageUri: String) -> Unit,
    onApplyVisionBoostToOriginal: (entityId: Long, rawBlockId: String, cacheKey: String, clearCacheAfter: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlight = remember(rawHighlight) {
        KnowledgeDetailHighlightQuery.effectiveHighlight(rawHighlight)
    }

    val pdfRef = remember(entity.source) { PdfSourceRef.parse(entity.source) }
    val blocksJsonNotBlank = remember(entity.contentBlocksJson) { !entity.contentBlocksJson.isNullOrBlank() }

    val blocksComputed by produceBlocksComputedState(
        entityId = entity.id,
        contentBlocksJson = entity.contentBlocksJson,
        highlight = highlight
    )

    val blocksLoading = blocksJsonNotBlank && blocksComputed == null
    val blocks = blocksComputed?.blocks.orEmpty()
    val hasBlocks = !blocksLoading && blocks.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (pdfRef != null) {
            KnowledgeDetailPdfButton(
                navController = navController,
                pdfRef = pdfRef,
                pageNumber = entity.pageNumber,
                bboxJson = entity.bboxJson
            )
        }

        when {
            blocksLoading -> {
                KnowledgeDetailCenteredLoading(modifier = Modifier.fillMaxSize())
            }

            hasBlocks -> {
                KnowledgeDetailBlocksContent(
                    blocks = blocks,
                    highlight = highlight,
                    matchIndices = blocksComputed?.matchIndices.orEmpty(),
                    initialBlockIndex = initialBlockIndex,
                    initialBlockId = initialBlockId,
                    entityId = entity.id,
                    pageNumber = entity.pageNumber,
                    imageUrisJson = entity.imageUris,
                    pdfRef = pdfRef,
                    deepLogicValidationEnabled = deepLogicValidationEnabled,
                    onToggleDeepLogicValidation = onToggleDeepLogicValidation,
                    visionMarkdownByBlockId = visionMarkdownByBlockId,
                    visionBoostingBlockId = visionBoostingBlockId,
                    visionBoostErrorBlockId = visionBoostErrorBlockId,
                    visionBoostErrorMessage = visionBoostErrorMessage,
                    onRequestVisionBoost = { blockId, imageUri ->
                        onRequestVisionBoost(entity.id, blockId, imageUri)
                    },
                    onApplyVisionBoostToOriginal = { rawBlockId, cacheKey, clearCacheAfter ->
                        onApplyVisionBoostToOriginal(entity.id, rawBlockId, cacheKey, clearCacheAfter)
                    },
                    onOpenPdfAtBox = { page, bboxJson ->
                        if (pdfRef != null) {
                            val bboxEncoded = bboxJson?.let { android.net.Uri.encode(it) }
                            navController.navigate(
                                com.example.powerai.navigation.Screen.PdfViewer.createRoute(
                                    fileId = pdfRef.fileId,
                                    name = android.net.Uri.encode(pdfRef.fileName),
                                    page = page,
                                    bboxEncoded = bboxEncoded
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                KnowledgeDetailFallbackContent(
                    content = entity.content.orEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
