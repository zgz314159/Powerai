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

/** State + callbacks for the rebuild block, bundled to keep drawer call sites short. */
internal data class KbRebuildUiState(
    val state: KbRebuildState,
    val confirmVisible: Boolean,
    val onRequest: () -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
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
                    "重建会清除本应用的全部知识条目、搜索索引与导入记录，再用当前内置资源重新建立；" +
                        "手工导入的记录也会被清除，可用原始文件重新导入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = ui.onRequest) {
                Text("重建内置知识库")
            }
            RebuildStatusText(state = ui.state)
        }
    }

    if (ui.confirmVisible) {
        AlertDialog(
            onDismissRequest = ui.onCancel,
            title = { Text("重建内置知识库？") },
            text = {
                Text("将删除本应用内的全部知识条目、搜索索引与导入记录，并从当前内置资源重新建立。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = ui.onConfirm) { Text("重建") }
            },
            dismissButton = {
                TextButton(onClick = ui.onCancel) { Text("取消") }
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
