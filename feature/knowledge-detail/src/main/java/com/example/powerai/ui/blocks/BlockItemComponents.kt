package com.example.powerai.ui.blocks







import com.example.powerai.core.model.UnknownBlock
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TextBlock
import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/**
 * Text block renderer - handles paragraph, heading, quote styles.
 */
@Composable
internal fun TextBlockItem(block: TextBlock, highlight: String) {
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

/**
 * Image block renderer - displays images loaded via Glide with optional click-to-zoom.
 */
@Composable
internal fun ImageBlockItem(block: ImageBlock, subfolder: String? = null) {
    val normalized = remember(block.src, subfolder) { normalizeImageSrc(block.src) }
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
                        try { context.startActivity(intent) } catch (_: Throwable) {}
                    }
                }
            },
            update = { imageView -> loadImageIntoView(imageView, normalized) }
        )
        ImageCaption(caption = block.caption, alt = block.alt)
    }
}

@Composable
private fun ImageCaption(caption: String?, alt: String?) {
    val captionText = caption?.trim().orEmpty()
    val altText = alt?.trim().orEmpty()
    val displayText = captionText.ifBlank { altText }

    if (displayText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * List block renderer - handles ordered and unordered lists with highlighting.
 */
@Composable
internal fun ListBlockItem(block: ListBlock, highlight: String) {
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

/**
 * Table block renderer - prefers snapshot image; shows placeholder if unavailable.
 */
@Composable
internal fun TableBlockItem(block: TableBlock, highlight: String) {
    val snapshotUri = remember(block.imageUri) {
        block.imageUri?.let { normalizeImageSrc(it) }?.trim().orEmpty()
    }

    if (snapshotUri.isNotBlank()) {
        TableSnapshotImage(snapshotUri)
    } else {
        TablePlaceholder()
    }
}

@Composable
private fun TableSnapshotImage(snapshotUri: String) {
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
                        try { ctx.startActivity(intent) } catch (_: Throwable) {}
                    }
                }
            },
            update = { imageView -> loadImageIntoView(imageView, snapshotUri) }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "表格原件（点击放大查看）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TablePlaceholder() {
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

/**
 * Code block renderer - displays code with monospace font in a styled container.
 */
@Composable
internal fun CodeBlockItem(block: CodeBlock) {
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

/**
 * Unknown/fallback block renderer - displays raw text if present.
 */
@Composable
internal fun UnknownBlockItem(block: UnknownBlock, highlight: String) {
    if (block.rawText.isNotBlank()) {
        Text(text = highlightAll(block.rawText, highlight), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Load image into ImageView using Glide with standard options.
 */
private fun loadImageIntoView(imageView: ImageView, uri: String) {
    try {
        val displayWidth = imageView.resources.displayMetrics.widthPixels
        val options = com.bumptech.glide.request.RequestOptions()
            .fitCenter()
            .override(displayWidth)

        Glide.with(imageView)
            .load(uri)
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean) = false
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean) = false
            })
            .into(imageView)
    } catch (_: Throwable) {}
}

/**
 * Normalize image source URI.
 */
internal fun normalizeImageSrc(src: String): String {
    return AssetImageUriNormalizer.normalize(src)
}
