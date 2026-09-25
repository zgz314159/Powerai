package com.example.powerai.ui.screen.detail



import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.KnowledgeBlock
import android.content.Intent
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide

private fun resolvePageNumber(block: KnowledgeBlock, fallback: Int?): Int? =
    block.pageNumber ?: fallback

private fun trimAndEmpty(text: String?): String =
    text?.trim().orEmpty()

@Composable
internal fun MagicWindowImageDisplay(
    imageUri: String,
    pageNumber: Int?,
    block: KnowledgeBlock
) {
    val effectivePageNumber = resolvePageNumber(block, pageNumber)
    val pageText = effectivePageNumber?.let { "第${it}页" }.orEmpty()
    
    if (pageText.isNotBlank()) {
        Text(text = pageText, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
    }

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
}

@Composable
internal fun MagicWindowControls(
    block: KnowledgeBlock,
    pageNumber: Int?,
    pdfRef: com.example.powerai.util.PdfSourceRef.Ref?,
    deepLogicValidationEnabled: Boolean,
    onToggleDeepLogicValidation: (Boolean) -> Unit,
    visionMarkdown: String?,
    isVisionBoosting: Boolean,
    onRequestVisionBoost: (blockId: String, imageUri: String) -> Unit,
    onApplyToOriginal: (clearCacheAfter: Boolean) -> Unit,
    onOpenPdfAtBox: (pageNumber: Int?, bboxJson: String?) -> Unit,
    imageUri: String,
    clearCacheAfterApply: Boolean,
    onClearCacheAfterApplyChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val effectivePageNumber = resolvePageNumber(block, pageNumber)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
        TableControlsSection(
            block = block,
            deepLogicValidationEnabled = deepLogicValidationEnabled,
            onToggleDeepLogicValidation = onToggleDeepLogicValidation,
            visionMarkdown = visionMarkdown,
            isVisionBoosting = isVisionBoosting,
            onRequestVisionBoost = onRequestVisionBoost,
            imageUri = imageUri,
            clearCacheAfterApply = clearCacheAfterApply,
            onClearCacheAfterApplyChanged = onClearCacheAfterApplyChanged,
            onApplyToOriginal = onApplyToOriginal
        )
    }
}

@Composable
private fun TableControlsSection(
    block: TableBlock,
    deepLogicValidationEnabled: Boolean,
    onToggleDeepLogicValidation: (Boolean) -> Unit,
    visionMarkdown: String?,
    isVisionBoosting: Boolean,
    onRequestVisionBoost: (blockId: String, imageUri: String) -> Unit,
    imageUri: String,
    clearCacheAfterApply: Boolean,
    onClearCacheAfterApplyChanged: (Boolean) -> Unit,
    onApplyToOriginal: (clearCacheAfter: Boolean) -> Unit
) {
    // Deep Logic Validation
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "深度逻辑验证（DeepSeek",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "对 AI 还原结果做一致性校验",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = deepLogicValidationEnabled,
            onCheckedChange = { onToggleDeepLogicValidation(it) }
        )
    }

    OutlinedButton(
        onClick = {
            val blockId = trimAndEmpty(block.id)
            if (blockId.isNotBlank()) {
                onRequestVisionBoost(blockId, imageUri)
            }
        },
        enabled = !isVisionBoosting
    ) {
        Text(if (isVisionBoosting) "AI 视觉解析中" else "AI 视觉深度解析")
    }

    val canApply = !visionMarkdown.isNullOrBlank() && trimAndEmpty(block.id).isNotBlank()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "回填后清除缓",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "避免后续继续用缓存覆盖渲",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = clearCacheAfterApply,
            onCheckedChange = { onClearCacheAfterApplyChanged(it) }
        )
    }

    OutlinedButton(
        onClick = { onApplyToOriginal(clearCacheAfterApply) },
        enabled = canApply && !isVisionBoosting
    ) {
        Text("应用到原")
    }
}

@Composable
internal fun MagicWindowDebugInfo(
    block: KnowledgeBlock,
    imageUri: String,
    visionMarkdown: String?,
    visionBoostErrorMessage: String?
) {
    val err = trimAndEmpty(visionBoostErrorMessage)
    if (err.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "VisionBoost 失败：${err.take(220)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    val vm = trimAndEmpty(visionMarkdown)
    if (vm.isNotBlank()) {
        Text(
            text = "AI 增强结果（已缓存）：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = vm.take(800),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Minimal debug info (kept short)
    val bb = trimAndEmpty(block.boundingBox)
    if (bb.isNotBlank()) {
        Text(
            text = "bbox=${bb.take(160)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val uriText = imageUri.trim().take(160)
    if (uriText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "imageUri=$uriText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
}
