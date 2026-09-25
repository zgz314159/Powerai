package com.example.powerai.ui.screen.detail




import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.KnowledgeBlock
import android.content.Intent
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.example.powerai.ui.blocks.KnowledgeBlockItem
import com.example.powerai.ui.image.AssetImageUriNormalizer
import com.example.powerai.util.PdfSourceRef

@Composable
internal fun KnowledgeDetailBlocksContent(
    displayParams: BlocksContentDisplayParams,
    resourceParams: BlocksDocumentResourceParams,
    visionParams: BlocksVisionBoostingParams = BlocksVisionBoostingParams(),
    validationParams: BlocksValidationParams = BlocksValidationParams(),
    interactionParams: BlocksInteractionParams = BlocksInteractionParams()
) {
    val listState = rememberLazyListState()

    fun cacheKey(blockId: String?): String? {
        val raw = blockId?.trim().orEmpty()
        if (raw.isBlank()) return null
        return if (validationParams.deepLogicValidationEnabled) "$raw::logic" else raw
    }

    // Magic Window state
    var magicWindowBlock by remember { mutableStateOf<KnowledgeBlock?>(null) }
    var magicWindowImageUri by remember { mutableStateOf<String?>(null) }

    val initialScrollIndex = remember(displayParams.blocks, displayParams.initialBlockIndex, displayParams.initialBlockId) {
        KnowledgeDetailBlocksScrollTargets.initialScrollIndex(
            blocks = displayParams.blocks,
            initialBlockIndex = displayParams.initialBlockIndex,
            initialBlockId = displayParams.initialBlockId
        )
    }

    LaunchedEffect(initialScrollIndex) {
        if (initialScrollIndex != null) {
            listState.scrollToItemCatching(initialScrollIndex)
        }
    }

    KnowledgeDetailMatchNavigationController(
        matchIndices = displayParams.matchIndices,
        listState = listState
    )

    if (magicWindowBlock != null && !magicWindowImageUri.isNullOrBlank()) {
        val rawBlockId = magicWindowBlock?.id?.trim().orEmpty()
        val cacheKeyForBlock = cacheKey(magicWindowBlock?.id)
        MagicWindowBottomSheet(
            entityId = resourceParams.entityId,
            block = magicWindowBlock!!,
            pageNumber = resourceParams.pageNumber,
            imageUri = magicWindowImageUri!!,
            pdfRef = resourceParams.pdfRef,
            deepLogicValidationEnabled = validationParams.deepLogicValidationEnabled,
            onToggleDeepLogicValidation = validationParams.onToggleDeepLogicValidation,
            visionMarkdown = cacheKey(magicWindowBlock?.id)?.let { visionParams.visionMarkdownByBlockId[it] },
            isVisionBoosting = cacheKey(magicWindowBlock?.id) == visionParams.visionBoostingBlockId,
            visionBoostErrorMessage = if (cacheKey(magicWindowBlock?.id) == visionParams.visionBoostErrorBlockId) visionParams.visionBoostErrorMessage else null,
            onRequestVisionBoost = { blockId, imageUri ->
                visionParams.onRequestVisionBoost(blockId, imageUri)
            },
            onApplyToOriginal = { clearCacheAfter ->
                if (rawBlockId.isNotBlank() && !cacheKeyForBlock.isNullOrBlank()) {
                    visionParams.onApplyVisionBoostToOriginal(rawBlockId, cacheKeyForBlock, clearCacheAfter)
                }
            },
            onOpenPdfAtBox = interactionParams.onOpenPdfAtBox,
            onDismiss = {
                magicWindowBlock = null
                magicWindowImageUri = null
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = displayParams.modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(displayParams.blocks) { _, block ->
            val canOpenMagicWindow = block !is ImageBlock && !block.boundingBox.isNullOrBlank()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canOpenMagicWindow) {
                            Modifier.clickable {
                                val chosen = block.imageUri
                                    ?.let { AssetImageUriNormalizer.normalize(it) }
                                    ?: SnapshotUriSelector.select(
                                        imageUrisJson = resourceParams.imageUrisJson,
                                        pageNumber = block.pageNumber ?: resourceParams.pageNumber,
                                        blockId = block.id,
                                        isTable = block is TableBlock
                                    )
                                if (!chosen.isNullOrBlank()) {
                                    magicWindowBlock = block
                                    magicWindowImageUri = chosen
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                val cached = cacheKey(block.id)?.let { visionParams.visionMarkdownByBlockId[it] }
                if (block is TableBlock && !cached.isNullOrBlank()) {
                    MarkdownChunkTextView(markdown = cached)
                } else {
                    KnowledgeDetailReadingBlockItem(
                        block = block,
                        highlight = displayParams.highlight,
                        fontScale = displayParams.fontScale
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
private fun MagicWindowBottomSheet(
    entityId: Long,
    block: KnowledgeBlock,
    pageNumber: Int?,
    imageUri: String,
    pdfRef: PdfSourceRef.Ref?,
    deepLogicValidationEnabled: Boolean,
    onToggleDeepLogicValidation: (Boolean) -> Unit,
    visionMarkdown: String?,
    isVisionBoosting: Boolean,
    visionBoostErrorMessage: String?,
    onRequestVisionBoost: (blockId: String, imageUri: String) -> Unit,
    onApplyToOriginal: (clearCacheAfter: Boolean) -> Unit,
    onOpenPdfAtBox: (pageNumber: Int?, bboxJson: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var clearCacheAfterApply by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(text = "点击查看原件", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

            MagicWindowImageDisplay(
                imageUri = imageUri,
                pageNumber = pageNumber,
                block = block
            )

            MagicWindowControls(
                block = block,
                pageNumber = pageNumber,
                pdfRef = pdfRef,
                deepLogicValidationEnabled = deepLogicValidationEnabled,
                onToggleDeepLogicValidation = onToggleDeepLogicValidation,
                visionMarkdown = visionMarkdown,
                isVisionBoosting = isVisionBoosting,
                onRequestVisionBoost = onRequestVisionBoost,
                onApplyToOriginal = onApplyToOriginal,
                onOpenPdfAtBox = onOpenPdfAtBox,
                imageUri = imageUri,
                clearCacheAfterApply = clearCacheAfterApply,
                onClearCacheAfterApplyChanged = { clearCacheAfterApply = it },
                onDismiss = onDismiss
            )

            MagicWindowDebugInfo(
                block = block,
                imageUri = imageUri,
                visionMarkdown = visionMarkdown,
                visionBoostErrorMessage = visionBoostErrorMessage
            )
        }
    }
}
