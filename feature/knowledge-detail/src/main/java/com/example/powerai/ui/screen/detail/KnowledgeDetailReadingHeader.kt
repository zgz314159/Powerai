package com.example.powerai.ui.screen.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.util.PdfSourceRef

@Composable
internal fun KnowledgeDetailReadingHeader(
    entity: KnowledgeEntity,
    sourceFileName: String?,
    navController: androidx.navigation.NavHostController? = null,
    pdfRef: PdfSourceRef.Ref? = null,
    modifier: Modifier = Modifier
) {
    val sourceName = sourceFileName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: PdfSourceRef.userVisibleSource(entity.source).ifBlank { "资料条目" }

    val isLowFidelity = entity.source.contains("lowFidelity=true")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DetailSourcePanel(
            sourceName = sourceName,
            pageNumber = entity.pageNumber,
            category = entity.category
        )

        if (isLowFidelity) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "提示：当前为快速预览模式，排版可能不整齐。建议查看原 PDF 或使用离线高清知识库。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (navController != null && pdfRef != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                var showFidelityDialog by remember { mutableStateOf(false) }
                IconButton(onClick = { showFidelityDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "排版说明",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }

                if (showFidelityDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showFidelityDialog = false },
                        title = { Text("排版保真说明") },
                        text = {
                            Text("为了提升手机端检索与复制效率，我们为您提供了“流式阅读模式”（即当前看到的文字样式）。\n\n如果您需要查看 100% 还原的官方排版、印章或复杂的图文关系，请点击顶栏切换至“原文”模式。系统将自动为您定位至当前内容所在的原始位置。")
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { showFidelityDialog = false }) {
                                Text("知道")
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun DetailSourcePanel(
    sourceName: String,
    pageNumber: Int?,
    category: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = sourceName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 20.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pageNumber?.let { DetailMetaChip(text = "原文页码 p${it}") }
            category.takeIf { it.isNotBlank() }?.let { DetailMetaChip(text = "分类 $it") }
        }
    }
}

@Composable
private fun DetailMetaChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
