package com.example.powerai.ui.screen.detail

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
internal fun MarkdownTableCompose(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val parsedState by produceState<ParsedTable>(
        initialValue = ParsedTable(header = emptyList(), rows = emptyList(), maxCols = -1),
        key1 = markdown
    ) {
        value = ParsedTable(header = emptyList(), rows = emptyList(), maxCols = -1)
        val start = System.currentTimeMillis()
        Log.d(
            "PowerAi.Trace",
            "tableCompose parse START len=${markdown.length} thread=${Thread.currentThread().name}"
        )
        value = withContext(Dispatchers.Default) {
            Log.d(
                "PowerAi.Trace",
                "tableCompose parse BG START len=${markdown.length} thread=${Thread.currentThread().name}"
            )
            runCatching {
                withTimeout(1200) {
                    // IMPORTANT: do NOT call ensureTableSeparators() here.
                    // That pipeline can be very expensive and, for some inputs, appear to never finish.
                    // We only need a readable table, so we use a simple, deterministic parser.

                    fun isSeparatorRow(line: String): Boolean {
                        val cells = MarkdownTableCellsSplitter.splitCellsPreserveTrailingEmpty(line)
                        if (cells.isEmpty()) return false
                        for (cell in cells) {
                            val trimmed = cell.trim()
                            if (trimmed.isEmpty()) return false
                            // Allow only -, :, and spaces
                            for (ch in trimmed) {
                                if (ch != '-' && ch != ':' && ch != ' ') return false
                            }
                            // Require at least 3 dashes somewhere to be a valid separator cell
                            if (trimmed.count { it == '-' } < 3) return false
                        }
                        return true
                    }

                    val normalized = markdown.replace("\r\n", "\n")
                    val tableLines = ArrayList<String>(32)
                    var lineCount = 0
                    for (line in normalized.lineSequence()) {
                        val t = line.trimEnd()
                        if (t.isBlank()) continue
                        if (!t.trimStart().startsWith("|")) continue
                        if (t.count { it == '|' } < 2) continue
                        tableLines.add(t)
                        lineCount++
                        if (lineCount >= 120) break
                    }

                    val rawCells = tableLines
                        .map { MarkdownTableCellsSplitter.splitCellsPreserveTrailingEmpty(it) }
                        .filter { it.isNotEmpty() }

                    val header = rawCells.getOrNull(0).orEmpty()
                    val hasSeparator = rawCells.getOrNull(1)?.let { _ -> isSeparatorRow(tableLines.getOrNull(1).orEmpty()) } == true
                    val data = if (rawCells.size >= 2) rawCells.drop(if (hasSeparator) 2 else 1) else emptyList()

                    val maxColsRaw = (rawCells.maxOfOrNull { it.size } ?: 0).coerceAtLeast(0)

                    // Safety caps to avoid pathological tables freezing UI.
                    val maxCols = maxColsRaw.coerceAtMost(12).coerceAtLeast(1)
                    val maxRows = data.size.coerceAtMost(60)

                    ParsedTable(
                        header = header.take(maxCols),
                        rows = data.take(maxRows).map { it.take(maxCols) },
                        maxCols = maxCols
                    )
                }
            }.getOrElse { t ->
                Log.w("PowerAi.Trace", "tableCompose parse FAILED/timeout", t)
                ParsedTable(header = emptyList(), rows = emptyList(), maxCols = 0)
            }
        }
        Log.d(
            "PowerAi.Trace",
            "tableCompose parse DONE rows=${value.rows.size} cols=${value.maxCols} took=${System.currentTimeMillis() - start}ms"
        )
    }
    val rows = parsedState

    if (rows.maxCols == -1) {
        // Loading: keep UI responsive and visible while parsing happens in background.
        Text(
            text = "表格解析中…",
            modifier = modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    if (rows.maxCols <= 0) {
        Text(
            text = markdown,
            modifier = modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    val scroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
    ) {
        // Header
        if (rows.header.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until rows.maxCols) {
                    val cell = rows.header.getOrNull(c).orEmpty()
                    Box(
                        modifier = Modifier
                            .border(1.dp, borderColor)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Body
        rows.rows.forEach { rowCells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until rows.maxCols) {
                    val cell = rowCells.getOrNull(c).orEmpty()
                    Box(
                        modifier = Modifier
                            .border(1.dp, borderColor)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private data class ParsedTable(
    val header: List<String>,
    val rows: List<List<String>>,
    val maxCols: Int
)
