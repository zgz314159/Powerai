@file:Suppress("MagicNumber")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.example.powerai.data.importer.KbRebuildState
import com.example.powerai.ui.screen.database.ClearAllConfirmStep

/** State + callbacks for the rebuild / clear-all block, bundled to keep drawer call sites short. */
internal data class KbRebuildUiState(
    val state: KbRebuildState,
    val confirmVisible: Boolean,
    val clearAllStep: ClearAllConfirmStep,
    val onRequest: () -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
    val onRequestClearAll: () -> Unit,
    val onContinueClearAll: () -> Unit,
    val onConfirmClearAll: () -> Unit,
    val onCancelClearAll: () -> Unit,
)

@Composable
internal fun KbRebuildBlock(
    ui: KbRebuildUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFFEEF3E8),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "内置知识库",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "重建只清除并按当前内置资源重装**可确认归属的内置知识库**；用户导入的知识库包、" +
                        "手工导入的资料以及无法确认归属的旧数据都会保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = ui.onRequest) {
                Text("重建内置知识库")
            }
            Text(
                text = "如需删除全部知识（含用户导入包、手工导入与旧数据），请使用下方入口：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = ui.onRequestClearAll) {
                Text("清空全部知识…", color = Color(0xFFB3261E))
            }
            RebuildStatusText(state = ui.state)
        }
    }

    KbRebuildDialogs(ui)
}

@Composable
private fun KbRebuildDialogs(ui: KbRebuildUiState) {
    if (ui.confirmVisible) {
        AlertDialog(
            onDismissRequest = ui.onCancel,
            title = { Text("重建内置知识库？") },
            text = {
                Text(
                    "将清除并重装可确认归属的内置知识库条目、搜索索引与导入记录。" +
                        "用户导入的知识库包、手工导入资料与无法确认归属的旧数据会保留。此操作不可撤销。",
                )
            },
            confirmButton = {
                TextButton(onClick = ui.onConfirm) { Text("重建") }
            },
            dismissButton = {
                TextButton(onClick = ui.onCancel) { Text("取消") }
            },
        )
    }

    if (ui.clearAllStep == ClearAllConfirmStep.SCOPE) {
        AlertDialog(
            onDismissRequest = ui.onCancelClearAll,
            title = { Text("清空全部知识？") },
            text = {
                Text(
                    "这将删除本应用内的全部知识条目、搜索索引、导入记录，包括：用户导入的知识库包、" +
                        "手工导入的资料、无法确认归属的旧数据。共享 PDF、内置知识库资源文件与用户包私有截图不受影响，但相关条目会被删除。",
                )
            },
            confirmButton = {
                TextButton(onClick = ui.onContinueClearAll) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = ui.onCancelClearAll) { Text("取消") }
            },
        )
    }

    if (ui.clearAllStep == ClearAllConfirmStep.FINAL) {
        AlertDialog(
            onDismissRequest = ui.onCancelClearAll,
            title = { Text("再次确认：清空全部知识") },
            text = {
                Text("请再次确认。此操作会删除全部知识条目且不可撤销，完成后仅重新导入内置知识库资源。")
            },
            confirmButton = {
                TextButton(onClick = ui.onConfirmClearAll) { Text("清空全部") }
            },
            dismissButton = {
                TextButton(onClick = ui.onCancelClearAll) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RebuildStatusText(state: KbRebuildState) {
    val status: Pair<String, Color>? =
        when (state) {
            KbRebuildState.Idle -> null
            is KbRebuildState.Running -> {
                val suffix = state.currentName?.let { " · $it" }.orEmpty()
                "重建中 ${state.processed}/${state.total}$suffix" to Color(0xFF7A5C00)
            }
            is KbRebuildState.Success ->
                "重建完成：${state.knowledgeRows} 条 · ${state.packages} 个资源" to Color(0xFF1B5E20)
            is KbRebuildState.Failed -> "重建失败：${state.reason}" to Color(0xFFB3261E)
            KbRebuildState.Cancelled -> "重建已取消，可重试" to Color(0xFF8A5A00)
        }
    if (status == null) return
    Text(text = status.first, style = MaterialTheme.typography.bodySmall, color = status.second)
}
