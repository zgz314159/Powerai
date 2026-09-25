package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DatabaseImportDiagnosticsPanel(
    diagnostics: com.example.powerai.data.importer.AssetImportDiagnostics,
    progress: com.example.powerai.data.importer.ImportProgress?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val issueEntries = diagnostics.entries.filter {
        it.status == "failed" || it.status == "missing" || (it.status == "imported" && it.rowCount <= 0)
    }
    val displayEntries = if (issueEntries.isNotEmpty()) issueEntries else diagnostics.entries

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFFF5F1E8),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "导入诊断",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onRefresh) {
                    Text(text = "重扫")
                }
            }

            Text(
                text = buildString {
                    append("扫描 ${diagnostics.scannedCount} ")
                    append(" · 成功 ${diagnostics.importedCount} ")
                    if (diagnostics.failedCount > 0) append(" · 失败 ${diagnostics.failedCount} ")
                    if (diagnostics.zeroRowCount > 0) append(" · 零条 ${diagnostics.zeroRowCount} ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            progress?.takeIf { it.status == "in_progress" }?.let {
                Text(
                    text = "当前导入${it.fileName} · ${it.importedItems}/${it.totalItems ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A5C00)
                )
            }

            if (displayEntries.isEmpty()) {
                Text(
                    text = "暂无诊断数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayEntries.forEach { entry ->
                    val statusLabel = when {
                        entry.status == "failed" -> "失败"
                        entry.status == "missing" -> "未导"
                        entry.status == "imported" && entry.rowCount <= 0 -> "已导入但 0 "
                        entry.status == "imported" -> "已导"
                        entry.status == "in_progress" -> "导入"
                        else -> entry.status.ifBlank { "未知" }
                    }
                    val statusColor = when {
                        entry.status == "failed" -> Color(0xFFB3261E)
                        entry.status == "missing" -> Color(0xFF8A5A00)
                        entry.status == "imported" && entry.rowCount <= 0 -> Color(0xFF8A5A00)
                        entry.status == "imported" -> Color(0xFF1B5E20)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$statusLabel · ${entry.rowCount} ",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                        entry.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
