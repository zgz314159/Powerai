package com.example.powerai.ui.screen.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.ui.blocks.*
import com.example.powerai.ui.image.AssetImageUriNormalizer
import com.example.powerai.ui.text.highlightAll

@Composable
internal fun DetailTableBlockItem(
    block: TableBlock,
    highlight: String,
    fontScale: Float,
    subfolder: String? = null
) {
    if (block.rows.isEmpty()) return

    val normalizedRows = remember(block.rows) { block.rows.map { row -> row.map { it.trim() } } }
    val header = normalizedRows.firstOrNull().orEmpty()
    val body = normalizedRows.drop(1)
    val hasMeaningfulHeader = header.count { it.isNotBlank() } >= 2
    val snapshotUri = remember(block.imageUri) {
        block.imageUri
            ?.let { AssetImageUriNormalizer.normalize(it) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    var showOriginalSnapshot by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (showOriginalSnapshot) Icons.Default.Info else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (showOriginalSnapshot) "表格原件 (物理存证)" else "表格内容 (结构化数据)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!snapshotUri.isNullOrBlank() && (!block.rows.isEmpty() || !block.cells.isNullOrEmpty())) {
                TextButton(
                    onClick = { showOriginalSnapshot = !showOriginalSnapshot },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                ) {
                    Text(
                        text = if (showOriginalSnapshot) "切换结构化" else "查看原文截图",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = showOriginalSnapshot,
            label = "table-mode-switch"
        ) { isOriginal ->
            if (isOriginal && !snapshotUri.isNullOrBlank()) {
                DetailTableSnapshot(snapshotUri = snapshotUri, subfolder = subfolder)
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!block.cells.isNullOrEmpty()) {
                            StructuredTableCompose(
                                cells = block.cells!!,
                                fontScale = fontScale
                            )
                        } else if (!hasMeaningfulHeader || body.isEmpty()) {
                            SelectionContainer {
                                Text(
                                    text = highlightAll(formatTableRowsForDetail(normalizedRows), highlight),
                                    style = detailParagraphStyle(fontScale, applyIndent = false),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            }
                        } else {
                            Text(
                                text = header.filter { it.isNotBlank() }.joinToString(" / "),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = scaledSp(18f, fontScale),
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = scaledSp(28f, fontScale)
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                            )

                            body.forEachIndexed { index, row ->
                                val fields = header.mapIndexedNotNull { cellIndex, title ->
                                    val value = row.getOrNull(cellIndex)?.trim().orEmpty()
                                    if (title.isBlank() || value.isBlank()) return@mapIndexedNotNull null
                                    title to value
                                }
                                if (fields.isEmpty()) return@forEachIndexed

                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    fields.forEach { (title, value) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.4.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            SelectionContainer {
                                                Text(
                                                    text = highlightAll(value, highlight),
                                                    style = detailParagraphStyle(fontScale, applyIndent = false),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailTableSnapshot(snapshotUri: String, subfolder: String? = null) {
    val imageBlock = remember(snapshotUri) {
        ImageBlock(
            id = "detail-table-snapshot::$snapshotUri",
            src = snapshotUri,
            alt = null,
            caption = "表格原件",
            boundingBox = null,
            pageNumber = null,
            imageUri = snapshotUri
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ImageBlockItem(block = imageBlock, subfolder = subfolder)
        Text(
            text = "表格原件，点击可放大查看",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun formatTableRowsForDetail(rows: List<List<String>>): String {
    return rows.joinToString("\n\n") { row ->
        row.filter { it.isNotBlank() }.joinToString("  ") { cell -> cell.trim() }
    }.trim()
}
