package com.example.powerai.ui.screen.detail

import com.example.powerai.core.model.TableCell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.screen.pdf.parsePdfBoundingBoxOrNull
import com.example.powerai.ui.screen.detail.scaledSp

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/**
 * Renders a structured table with support for rowSpan and colSpan in Compose.
 */
@Composable
internal fun StructuredTableCompose(
    cells: List<TableCell>,
    fontScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (cells.isEmpty()) return

    val maxRow = cells.maxOf { it: TableCell -> (it.row + it.rowSpan - 1).toInt() }
    val maxCol = cells.maxOf { it: TableCell -> (it.col + it.colSpan - 1).toInt() }
    val rowCount = maxRow + 1
    val colCount = maxCol + 1

    val scrollState = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    val anyLowConfidence = cells.any { c -> val conf = c.confidence; conf != null && conf < 0.8f }

    Column(modifier = modifier.fillMaxWidth()) {
        if (anyLowConfidence) {
            Text(
                text = "⚠️ 部分单元AI 提取置信度较低，建议点击顶栏切换原文核对",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, MaterialTheme.shapes.medium)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                TableLayout(
                    cells = cells,
                    rowCount = rowCount,
                    colCount = colCount,
                    borderColor = borderColor
                ) {
                    cells.forEach { cell ->
                        val conf = cell.confidence
                        val lowConfidence = conf != null && conf < 0.8f
                        val backgroundColor = if (cell.isHeader) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else if (lowConfidence) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        } else if (cell.row % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        } else {
                            Color.Transparent
                        }

                        val isNumeric = cell.text.isNotEmpty() && cell.text.all { it.isDigit() || it == '.' || it == '-' || it == '%' || it == ' ' || it == '/' }
                        val textAlign = when (cell.alignment?.lowercase()) {
                            "center" -> androidx.compose.ui.text.style.TextAlign.Center
                            "right" -> androidx.compose.ui.text.style.TextAlign.Right
                            "left" -> androidx.compose.ui.text.style.TextAlign.Start
                            else -> when {
                                cell.isHeader -> androidx.compose.ui.text.style.TextAlign.Center
                                isNumeric -> androidx.compose.ui.text.style.TextAlign.Center
                                else -> androidx.compose.ui.text.style.TextAlign.Start
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(backgroundColor)
                                .border(0.5.dp, if (lowConfidence) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else borderColor)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cell.text,
                                style = if (cell.isHeader) {
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontSize = scaledSp(14f, fontScale),
                                        lineHeight = scaledSp(20f, fontScale),
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = textAlign
                                    )
                                } else {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = scaledSp(14f, fontScale),
                                        lineHeight = scaledSp(20f, fontScale),
                                        fontWeight = if (cell.text.length < 8 && cell.col == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = textAlign
                                    )
                                },
                                color = if (lowConfidence) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Scroll indicator (right side)
            if (scrollState.value < scrollState.maxValue) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(40.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Scroll",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableLayout(
    cells: List<TableCell>,
    rowCount: Int,
    colCount: Int,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Layout(content = content) { measurables, constraints ->
        // 1. Calculate physical width ratios from BBoxes if available
        val physicalWidths = FloatArray(colCount) { 0f }
        cells.forEach { cell ->
            if (cell.colSpan == 1 && !cell.boundingBox.isNullOrBlank()) {
                val bbox = parsePdfBoundingBoxOrNull(cell.boundingBox)
                if (bbox != null) {
                    val w = bbox.xMax - bbox.xMin
                    if (w > physicalWidths[cell.col]) {
                        physicalWidths[cell.col] = w
                    }
                }
            }
        }

        val totalPhysicalWidth = physicalWidths.sum()
        val hasPhysicalData = totalPhysicalWidth > 0f

        // Initial width per column: mix of physical ratio and content-based
        val minCellWidth = 80.dp.roundToPx()
        val colWidths = IntArray(colCount) { minCellWidth }
        val rowHeights = IntArray(rowCount) { 0 }

        val placeables = arrayOfNulls<Placeable>(measurables.size)

        // First pass: measure all cells
        measurables.forEachIndexed { index, measurable ->
            val cell = cells[index]
            val cellConstraints = Constraints(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            )
            val p = measurable.measure(cellConstraints)
            placeables[index] = p

            if (cell.colSpan == 1) {
                colWidths[cell.col] = max(colWidths[cell.col], p.width)
            }
            if (cell.rowSpan == 1) {
                rowHeights[cell.row] = max(rowHeights[cell.row], p.height)
            }
        }

        // Apply physical ratios if available, but respect min content widths
        if (hasPhysicalData) {
            val baseTableWidth = max(constraints.minWidth, colWidths.sum()).toFloat()
            for (c in 0 until colCount) {
                if (physicalWidths[c] > 0f) {
                    val targetW = (physicalWidths[c] / totalPhysicalWidth * baseTableWidth).toInt()
                    colWidths[c] = max(colWidths[c], targetW)
                }
            }
        }

        // Second pass: refine multi-span cells
        cells.forEachIndexed { index, cell ->
            val p = placeables[index]!!
            if (cell.colSpan > 1) {
                val currentSpanWidth = (cell.col until (cell.col + cell.colSpan)).sumOf { colWidths[it] }
                if (p.width > currentSpanWidth) {
                    val extra = (p.width - currentSpanWidth) / cell.colSpan
                    for (c in cell.col until (cell.col + cell.colSpan)) colWidths[c] += extra
                }
            }
            if (cell.rowSpan > 1) {
                val currentSpanHeight = (cell.row until (cell.row + cell.rowSpan)).sumOf { rowHeights[it] }
                if (p.height > currentSpanHeight) {
                    val extra = (p.height - currentSpanHeight) / cell.rowSpan
                    for (r in cell.row until (cell.row + cell.rowSpan)) rowHeights[r] += extra
                }
            }
        }

        val totalWidth = colWidths.sum()
        val totalHeight = rowHeights.sum()

        layout(totalWidth, totalHeight) {
            cells.forEachIndexed { index, cell ->
                val x = (0 until cell.col).sumOf { colWidths[it] }
                val y = (0 until cell.row).sumOf { rowHeights[it] }

                val targetWidth = (cell.col until (cell.col + cell.colSpan)).sumOf { colWidths[it] }
                val targetHeight = (cell.row until (cell.row + cell.rowSpan)).sumOf { rowHeights[it] }

                val p = measurables[index].measure(
                    Constraints.fixed(targetWidth, targetHeight)
                )
                p.placeRelative(x, y)
            }
        }
    }
}
