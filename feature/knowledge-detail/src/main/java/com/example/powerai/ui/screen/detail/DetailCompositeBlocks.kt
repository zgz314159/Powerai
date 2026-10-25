package com.example.powerai.ui.screen.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.ui.blocks.*
import com.example.powerai.ui.text.highlightAll

@Composable
internal fun DetailTableTextComposite(
    text: String,
    images: List<ImageBlock>,
    highlight: String,
    fontScale: Float,
    subfolder: String? = null,
    bottom: androidx.compose.ui.unit.Dp = 12.dp
) {
    if (images.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = bottom)
        ) {
            images.forEach { image ->
                ImageBlockItem(block = image, subfolder = subfolder)
            }
        }
    }

    if (text.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "表格摘要 (AI 提取)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                SelectionContainer {
                    Text(
                        text = highlightAll(text, highlight),
                        style = detailParagraphStyle(fontScale, applyIndent = false),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailStructuredTableComposite(
    table: TableBlock,
    images: List<ImageBlock>,
    highlight: String,
    fontScale: Float,
    subfolder: String? = null
) {
    DetailTableBlockItem(block = table, highlight = highlight, fontScale = fontScale, subfolder = subfolder)
    if (images.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            images.forEach { image ->
                ImageBlockItem(block = image, subfolder = subfolder)
            }
        }
    }
}

@Composable
internal fun DetailFigureComposite(
    image: ImageBlock,
    caption: TextBlock,
    highlight: String,
    fontScale: Float,
    subfolder: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ImageBlockItem(block = image, subfolder = subfolder)

            val captionText = highlightAll(caption.text, highlight)
            Text(
                text = captionText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = scaledSp(14f, fontScale),
                    lineHeight = scaledSp(20f, fontScale),
                    textAlign = TextAlign.Center
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
            )
        }
    }
}
