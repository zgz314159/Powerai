package com.example.powerai.ui.screen.detail





import com.example.powerai.core.model.UnknownBlock
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.TextBlock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.powerai.ui.blocks.*
import com.example.powerai.ui.text.highlightAll
import com.example.powerai.ui.screen.detail.scaledSp
import com.example.powerai.ui.screen.detail.detailParagraphStyle

@Composable
internal fun DetailTextBlockItem(
    block: TextBlock,
    highlight: String,
    fontScale: Float,
    onAnchorClick: ((String) -> Unit)? = null
) {
    if (block.text.isBlank()) return

    val baseFontSize = block.fontSize ?: when (block.style) {
        TextBlock.TextStyle.Heading1 -> 26f
        TextBlock.TextStyle.Heading2 -> 22f
        TextBlock.TextStyle.Heading3 -> 20f
        else -> 17f
    }

    val isBold = block.isBold ?: when (block.style) {
        TextBlock.TextStyle.Heading1, TextBlock.TextStyle.Heading2, TextBlock.TextStyle.Heading3 -> true
        else -> false
    }

    val isHeading = block.style == TextBlock.TextStyle.Heading1 ||
                    block.style == TextBlock.TextStyle.Heading2 ||
                    block.style == TextBlock.TextStyle.Heading3

    val textAlign = when (block.alignment?.lowercase()) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> if (isHeading) TextAlign.Start else TextAlign.Justify
    }

    val textTrimmed = block.text.trim()
    val isTier2List = textTrimmed.let {
        Regex("^[]\\d+[]").containsMatchIn(it) ||
        Regex("^[a-zA-Z][]\\s").containsMatchIn(it)
    }
    val isTier1List = !isTier2List && textTrimmed.let {
        it.startsWith("") ||
        it.startsWith("- ") ||
        Regex("^\\d+\\.\\s").containsMatchIn(it) ||
        Regex("^[一二三四五六七八九十百]+").containsMatchIn(it)
    }

    val startPadding = when {
        isTier2List && (textAlign == TextAlign.Start || textAlign == TextAlign.Justify) -> 28.dp
        isTier1List && (textAlign == TextAlign.Start || textAlign == TextAlign.Justify) -> 14.dp
        else -> 0.dp
    }

    val hasLinks = block.text.contains("[") && block.text.contains("](")

    if (hasLinks) {
        MarkdownChunkTextView(
            markdown = block.text,
            fontScale = fontScale,
            textAlign = textAlign,
            onAnchorClick = onAnchorClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp, start = startPadding)
        )
        return
    }

    val style = when (block.style) {
        TextBlock.TextStyle.Heading1 -> MaterialTheme.typography.headlineMedium.copy(
            fontSize = scaledSp(baseFontSize, fontScale),
            lineHeight = scaledSp(baseFontSize + 10f, fontScale),
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = textAlign
        )
        TextBlock.TextStyle.Heading2 -> MaterialTheme.typography.headlineSmall.copy(
            fontSize = scaledSp(baseFontSize, fontScale),
            lineHeight = scaledSp(baseFontSize + 10f, fontScale),
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = textAlign
        )
        TextBlock.TextStyle.Heading3 -> MaterialTheme.typography.titleLarge.copy(
            fontSize = scaledSp(baseFontSize, fontScale),
            lineHeight = scaledSp(baseFontSize + 10f, fontScale),
            fontWeight = if (isBold) FontWeight.Medium else FontWeight.Normal,
            textAlign = textAlign
        )
        TextBlock.TextStyle.Quote -> detailParagraphStyle(fontScale, applyIndent = false).copy(
            fontSize = scaledSp(baseFontSize, fontScale),
            lineHeight = scaledSp(baseFontSize + 11f, fontScale),
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = textAlign
        )
        TextBlock.TextStyle.Paragraph -> detailParagraphStyle(fontScale, applyIndent = !isTier1List && !isTier2List).copy(
            fontSize = scaledSp(baseFontSize, fontScale),
            lineHeight = scaledSp(baseFontSize + 11f, fontScale),
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = textAlign
        )
    }

    val text = highlightAll(block.text, highlight)

    if (block.style == TextBlock.TextStyle.Quote) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp, start = startPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = text,
                style = style,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        SelectionContainer {
            Text(
                text = text,
                style = style,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp, start = startPadding)
            )
        }
    }
}

@Composable
internal fun DetailListBlockItem(block: ListBlock, highlight: String, fontScale: Float) {
    if (block.items.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            if (item.isBlank()) return@forEachIndexed
            val prefix = if (block.ordered) "${index + 1}. " else ""

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = prefix,
                    style = detailParagraphStyle(fontScale, applyIndent = false),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(if (block.ordered) 28.dp else 16.dp)
                )
                Text(
                    text = highlightAll(item, highlight),
                    style = detailParagraphStyle(fontScale, applyIndent = false),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun DetailCodeBlockItem(block: CodeBlock, fontScale: Float) {
    if (block.code.isBlank()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        SelectionContainer {
            Text(
                text = block.code,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = scaledSp(16f, fontScale),
                    lineHeight = scaledSp(27f, fontScale),
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
internal fun DetailUnknownBlockItem(block: UnknownBlock, highlight: String, fontScale: Float) {
    if (block.rawText.isBlank()) return
    Text(
        text = highlightAll(block.rawText, highlight),
        style = detailParagraphStyle(fontScale, applyIndent = false),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
internal fun detailParagraphStyle(fontScale: Float, applyIndent: Boolean = true): TextStyle {
    val fontSize = scaledSp(17f, fontScale)
    return MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.62f,
        letterSpacing = 0.4.sp,
        textIndent = if (applyIndent) TextIndent(firstLine = fontSize * 2) else TextIndent.None,
        textAlign = TextAlign.Justify,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto
    )
}
