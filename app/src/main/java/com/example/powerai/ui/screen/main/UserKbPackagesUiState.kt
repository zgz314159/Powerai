package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.powerai.data.importer.UserKbPackageSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** State + callbacks for the "用户知识库" management block. */
internal data class UserKbPackagesUiState(
    val packages: List<UserKbPackageSummary>,
    val pendingRemovePackageId: String?,
    val message: String?,
    val onImportDirectory: () -> Unit,
    val onUpdateDirectory: () -> Unit,
    val onRequestRemove: (String) -> Unit,
    val onConfirmRemove: () -> Unit,
    val onCancelRemove: () -> Unit,
)

/**
 * "用户知识库" block: import a directory, list the already-imported packages with entry count,
 * version time and PDF association, update a package by re-selecting its directory (unchanged
 * content is skipped) and remove exactly one package after confirmation.
 */
@Suppress("MagicNumber")
@Composable
internal fun UserKbPackagesBlock(
    ui: UserKbPackagesUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFFE8F0F5),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "用户知识库",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "从手机目录导入 PaddleModels 生成的知识库（含 knowledge_base.json 与 shots），" +
                        "无需重新打包应用；导入后可直接搜索、查看表格截图并关联原 PDF。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = ui.onImportDirectory) {
                Text("导入知识库目录")
            }
            ui.message?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7A5C00))
            }
            if (ui.packages.isEmpty()) {
                Text(
                    text = "尚未导入用户知识库包。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ui.packages.forEach { pkg ->
                    UserKbPackageRow(
                        pkg = pkg,
                        onUpdate = ui.onUpdateDirectory,
                        onRemove = { ui.onRequestRemove(pkg.packageId) },
                    )
                }
            }
        }
    }

    val pending = ui.packages.firstOrNull { it.packageId == ui.pendingRemovePackageId }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = ui.onCancelRemove,
            title = { Text("移除用户知识库包？") },
            text = {
                Text(
                    "将移除「${pending.displayName}」的 ${pending.entries} 条条目、搜索索引、导入记录与私有截图。" +
                        "不会删除共享 PDF、内置知识库、其他用户包或手工导入资料。此操作不可撤销。",
                )
            },
            confirmButton = { TextButton(onClick = ui.onConfirmRemove) { Text("移除") } },
            dismissButton = { TextButton(onClick = ui.onCancelRemove) { Text("取消") } },
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun UserKbPackageRow(
    pkg: UserKbPackageSummary,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Text(
            text = pkg.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "条目 ${pkg.entries} · 更新于 ${formatTimestamp(pkg.updatedAtMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pdfAssociationLabel(pkg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUpdate) { Text("更新（选同一目录）") }
            TextButton(onClick = onRemove) { Text("移除") }
        }
    }
}

private fun pdfAssociationLabel(pkg: UserKbPackageSummary): String =
    when {
        pkg.pdfFileName == null -> "PDF 关联：无"
        pkg.pdfAssociated -> "PDF 关联：${pkg.pdfFileName}（已关联）"
        else -> "PDF 关联：${pkg.pdfFileName}（未关联）"
    }

private fun formatTimestamp(millis: Long): String =
    if (millis <= 0L) {
        "未知"
    } else {
        runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis)) }
            .getOrDefault("未知")
    }
