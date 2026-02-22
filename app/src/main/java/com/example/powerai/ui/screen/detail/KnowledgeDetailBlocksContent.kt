package com.example.powerai.ui.screen.detail

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
import com.example.powerai.ui.blocks.ImageBlock
import com.example.powerai.ui.blocks.KnowledgeBlock
import com.example.powerai.ui.blocks.KnowledgeBlockItem
import com.example.powerai.ui.blocks.TableBlock
import com.example.powerai.ui.image.AssetImageUriNormalizer
import com.example.powerai.util.PdfSourceRef

@Composable
internal fun KnowledgeDetailBlocksContent(
    blocks: List<KnowledgeBlock>,
    highlight: String,
    matchIndices: List<Int>,
    initialBlockIndex: Int?,
    initialBlockId: String?,
    entityId: Long,
    pageNumber: Int?,
    imageUrisJson: String?,
    pdfRef: PdfSourceRef.Ref?,
    deepLogicValidationEnabled: Boolean,
    onToggleDeepLogicValidation: (Boolean) -> Unit,
    visionMarkdownByBlockId: Map<String, String>,
    visionBoostingBlockId: String?,
    visionBoostErrorBlockId: String?,
    visionBoostErrorMessage: String?,
    onRequestVisionBoost: (blockId: String, imageUri: String) -> Unit,
    onApplyVisionBoostToOriginal: (rawBlockId: String, cacheKey: String, clearCacheAfter: Boolean) -> Unit,
    onOpenPdfAtBox: (pageNumber: Int?, bboxJson: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    fun cacheKey(blockId: String?): String? {
        val raw = blockId?.trim().orEmpty()
        if (raw.isBlank()) return null
        return if (deepLogicValidationEnabled) "$raw::logic" else raw
    }

    // Magic Window state
    var magicWindowBlock by remember { mutableStateOf<KnowledgeBlock?>(null) }
    var magicWindowImageUri by remember { mutableStateOf<String?>(null) }

    val initialScrollIndex = remember(blocks, initialBlockIndex, initialBlockId) {
        KnowledgeDetailBlocksScrollTargets.initialScrollIndex(
            blocks = blocks,
            initialBlockIndex = initialBlockIndex,
            initialBlockId = initialBlockId
        )
    }

    LaunchedEffect(initialScrollIndex) {
        if (initialScrollIndex != null) {
            listState.scrollToItemCatching(initialScrollIndex)
        }
    }

    KnowledgeDetailMatchNavigationController(
        matchIndices = matchIndices,
        listState = listState
    )

    if (magicWindowBlock != null && !magicWindowImageUri.isNullOrBlank()) {
        val rawBlockId = magicWindowBlock?.id?.trim().orEmpty()
        val cacheKeyForBlock = cacheKey(magicWindowBlock?.id)
        MagicWindowBottomSheet(
            entityId = entityId,
            block = magicWindowBlock!!,
            pageNumber = pageNumber,
            imageUri = magicWindowImageUri!!,
            pdfRef = pdfRef,
            deepLogicValidationEnabled = deepLogicValidationEnabled,
            onToggleDeepLogicValidation = onToggleDeepLogicValidation,
            visionMarkdown = cacheKey(magicWindowBlock?.id)?.let { visionMarkdownByBlockId[it] },
            isVisionBoosting = cacheKey(magicWindowBlock?.id) == visionBoostingBlockId,
            visionBoostErrorMessage = if (cacheKey(magicWindowBlock?.id) == visionBoostErrorBlockId) visionBoostErrorMessage else null,
            onRequestVisionBoost = { blockId, imageUri ->
                onRequestVisionBoost(blockId, imageUri)
            },
            onApplyToOriginal = { clearCacheAfter ->
                if (rawBlockId.isNotBlank() && !cacheKeyForBlock.isNullOrBlank()) {
                    onApplyVisionBoostToOriginal(rawBlockId, cacheKeyForBlock, clearCacheAfter)
                }
            },
            onOpenPdfAtBox = onOpenPdfAtBox,
            onDismiss = {
                magicWindowBlock = null
                magicWindowImageUri = null
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(blocks) { _, block ->
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
                                        imageUrisJson = imageUrisJson,
                                        pageNumber = block.pageNumber ?: pageNumber,
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
                val cached = cacheKey(block.id)?.let { visionMarkdownByBlockId[it] }
                if (block is TableBlock && !cached.isNullOrBlank()) {
                    MarkdownChunkTextView(markdown = cached)
                } else {
                    KnowledgeBlockItem(block = block, highlight = highlight)
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

            val effectivePageNumber = block.pageNumber ?: pageNumber
            val pageText = effectivePageNumber?.let { "第${it}页" }.orEmpty()
            if (pageText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = pageText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setOnClickListener {
                            val intent = Intent(ctx, PhotoViewerActivity::class.java).apply {
                                putExtra(PhotoViewerActivity.EXTRA_IMAGE_URI, imageUri)
                            }
                            try { ctx.startActivity(intent) } catch (_: Throwable) {}
                        }
                    }
                },
                update = { imageView ->
                    try {
                        val displayWidth = imageView.resources.displayMetrics.widthPixels
                        val ro = com.bumptech.glide.request.RequestOptions().fitCenter().override(displayWidth)
                        Glide.with(imageView).load(imageUri).apply(ro).into(imageView)
                    } catch (_: Throwable) {
                    }
                }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val bbox = block.boundingBox
                        if (pdfRef != null && !bbox.isNullOrBlank()) {
                            onOpenPdfAtBox(effectivePageNumber, bbox)
                            onDismiss()
                        }
                    },
                    enabled = pdfRef != null && !block.boundingBox.isNullOrBlank()
                ) {
                    Text("在 PDF 中定位")
                }

                OutlinedButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }

            if (block is TableBlock) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "深度逻辑验证（DeepSeek）",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "对 AI 还原结果做一致性校验",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = deepLogicValidationEnabled,
                        onCheckedChange = { onToggleDeepLogicValidation(it) }
                    )
                }

                OutlinedButton(
                    onClick = {
                        val blockId = block.id?.trim().orEmpty()
                        if (blockId.isNotBlank()) {
                            onRequestVisionBoost(blockId, imageUri)
                        }
                    },
                    enabled = !isVisionBoosting
                ) {
                    Text(if (isVisionBoosting) "AI 视觉解析中…" else "AI 视觉深度解析")
                }

                val canApply = !visionMarkdown.isNullOrBlank() && block.id?.trim()?.isNotBlank() == true

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "回填后清除缓存",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "避免后续继续用缓存覆盖渲染",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = clearCacheAfterApply,
                        onCheckedChange = { clearCacheAfterApply = it }
                    )
                }

                OutlinedButton(
                    onClick = { onApplyToOriginal(clearCacheAfterApply) },
                    enabled = canApply && !isVisionBoosting
                ) {
                    Text("应用到原文")
                }
            }

            val err = visionBoostErrorMessage?.trim().orEmpty()
            if (err.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "VisionBoost 失败：${err.take(220)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val vm = visionMarkdown?.trim().orEmpty()
            if (vm.isNotBlank()) {
                Text(
                    text = "AI 增强结果（已缓存）：",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = vm.take(800),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Minimal debug info (kept short)
            val bb = block.boundingBox?.trim().orEmpty()
            if (bb.isNotBlank()) {
                Text(
                    text = "bbox=${bb.take(160)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val uriText = imageUri.trim().take(160)
            if (uriText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "imageUri=$uriText",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
