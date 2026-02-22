package com.example.powerai.ui.blocks

import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.powerai.ui.image.AssetImageUriNormalizer
import com.example.powerai.ui.screen.detail.PhotoViewerActivity
import com.example.powerai.ui.text.highlightAll

@Composable
fun KnowledgeBlocksColumn(
    blocks: List<KnowledgeBlock>,
    highlight: String,
    modifier: Modifier = Modifier
) {
    Log.d("PowerAi.Trace", "KnowledgeBlocksColumn render start count=${blocks.size}")
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(blocks) { _, block ->
            KnowledgeBlockItem(block = block, highlight = highlight)
        }
    }
    Log.d("PowerAi.Trace", "KnowledgeBlocksColumn render end")
}

@Composable
fun KnowledgeBlockItem(
    block: KnowledgeBlock,
    highlight: String
) {
    when (block) {
        is TextBlock -> TextBlockItem(block, highlight)
        is ImageBlock -> ImageBlockItem(block)
        is ListBlock -> ListBlockItem(block, highlight)
        is TableBlock -> TableBlockItem(block, highlight)
        is CodeBlock -> CodeBlockItem(block)
        is UnknownBlock -> {
            if (block.rawText.isNotBlank()) {
                Text(text = highlightAll(block.rawText, highlight), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TextBlockItem(block: TextBlock, highlight: String) {
    val style = when (block.style) {
        TextBlock.TextStyle.Heading1 -> MaterialTheme.typography.headlineSmall
        TextBlock.TextStyle.Heading2 -> MaterialTheme.typography.titleLarge
        TextBlock.TextStyle.Heading3 -> MaterialTheme.typography.titleMedium
        TextBlock.TextStyle.Quote -> MaterialTheme.typography.bodyMedium
        TextBlock.TextStyle.Paragraph -> MaterialTheme.typography.bodyMedium
    }

    if (block.text.isBlank()) return

    val text = highlightAll(block.text, highlight)

    if (block.style == TextBlock.TextStyle.Quote) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = style,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        Text(text = text, style = style)
    }
}

@Composable
private fun ImageBlockItem(block: ImageBlock) {
    val normalized = remember(block.src) { normalizeImageSrc(block.src) }

    if (normalized.isBlank()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.medium),
            factory = { context ->
                ImageView(context).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setOnClickListener {
                        val intent = Intent(context, PhotoViewerActivity::class.java).apply {
                            putExtra(PhotoViewerActivity.EXTRA_IMAGE_URI, normalized)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                        }
                    }
                }
            },
            update = { imageView ->
                try {
                    val displayWidth = imageView.resources.displayMetrics.widthPixels
                    val ro = com.bumptech.glide.request.RequestOptions()
                        .fitCenter()
                        .override(displayWidth)

                    Log.d("PowerAi.Trace", "Glide load START src=$normalized override=$displayWidth thread=${Thread.currentThread().name}")
                    Glide.with(imageView)
                        .load(normalized)
                        .apply(ro)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, targetGl: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                Log.w("PowerAi.Trace", "Glide load FAILED src=$normalized", e)
                                return false
                            }

                            override fun onResourceReady(resource: Drawable, model: Any, targetGl: Target<Drawable>, dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean): Boolean {
                                Log.d("PowerAi.Trace", "Glide load DONE src=$normalized thread=${Thread.currentThread().name}")
                                return false
                            }
                        })
                        .into(imageView)
                } catch (_: Throwable) {
                }
            }
        )

        val caption = block.caption?.trim().orEmpty()
        if (caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val alt = block.alt?.trim().orEmpty()
            if (alt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ListBlockItem(block: ListBlock, highlight: String) {
    if (block.items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEachIndexed { index, item ->
            if (item.isBlank()) return@forEachIndexed
            val prefix = if (block.ordered) "${index + 1}. " else "• "
            Text(
                text = buildAnnotatedString {
                    append(prefix)
                    append(highlightAll(item, highlight))
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TableBlockItem(block: TableBlock, highlight: String) {
    val snapshotUri = remember(block.imageUri) {
        block.imageUri?.let { normalizeImageSrc(it) }?.trim().orEmpty()
    }

    // Prefer rendering the attached original snapshot image if present.
    // This avoids misleading “reconstructed” tables for complex merged cells / legends.
    if (snapshotUri.isNotBlank()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.medium),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setOnClickListener {
                            val intent = Intent(ctx, PhotoViewerActivity::class.java).apply {
                                putExtra(PhotoViewerActivity.EXTRA_IMAGE_URI, snapshotUri)
                            }
                            try { ctx.startActivity(intent) } catch (_: Throwable) {
                            }
                        }
                    }
                },
                update = { imageView ->
                    try {
                        val displayWidth = imageView.resources.displayMetrics.widthPixels
                        val ro = com.bumptech.glide.request.RequestOptions()
                            .fitCenter()
                            .override(displayWidth)
                        Glide.with(imageView)
                            .load(snapshotUri)
                            .apply(ro)
                            .into(imageView)
                    } catch (_: Throwable) {
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "表格原件（点击放大查看）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // No fallback reconstruction: tables/legends should be viewed via “原件截图”.
    // We still keep the extracted text in the index for searching/AI, but UI should not render a guessed table.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "缺少表格原件截图（请在预处理阶段提供截图并设置 imageUri）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "提示：已保留可检索文本；仅禁用 UI 重建。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CodeBlockItem(block: CodeBlock) {
    val code = block.code
    if (code.isBlank()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        SelectionContainer {
            Text(
                text = code,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun normalizeImageSrc(src: String): String {
    return AssetImageUriNormalizer.normalize(src)
}
