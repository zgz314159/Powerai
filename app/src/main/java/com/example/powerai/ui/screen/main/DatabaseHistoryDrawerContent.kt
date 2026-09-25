package com.example.powerai.ui.screen.main
import com.example.powerai.domain.model.DatabaseFileGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon

@Composable
internal fun DatabaseHistoryDrawerContent(
    groups: List<DatabaseFileGroup>,
    currentQuery: String,
    isLoading: Boolean,
    diagnostics: com.example.powerai.data.importer.AssetImportDiagnostics,
    progress: com.example.powerai.data.importer.ImportProgress?,
    onSelectGroup: (String) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onEdgeAction: (() -> Unit)? = null
) {
    DrawerWrapper(
        title = "数据库原文件",
        actionLabel = "重扫诊断",
        onAction = onRefreshDiagnostics,
        onEdgeAction = onEdgeAction
    ) {
        val normalizedQuery = currentQuery.trim()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "drawer_diagnostics") {
                DatabaseImportDiagnosticsPanel(
                    diagnostics = diagnostics,
                    progress = progress,
                    onRefresh = onRefreshDiagnostics
                )
            }

            when {
                isLoading && groups.isEmpty() -> {
                    item(key = "drawer_loading") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }

                groups.isEmpty() -> {
                    item(key = "drawer_empty") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "暂无原始文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                else -> {
                    item(key = "drawer_directory_intro") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "原始文件目录",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (normalizedQuery.isBlank()) {
                                        "${groups.size} 份原始文件，点击可快速展开并定位对应词条组"
                                    } else {
                                        "目录：${groups.size} 份原始文件；当前关键词「${normalizedQuery}」下点击文件会先退出搜索再定位"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(groups, key = { it.key }) { group ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectGroup(group.key) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = group.fileName.ifBlank { "未命名文" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = buildString {
                                                append("命中 ${group.rows.size} ")
                                                append(" · 关联截图 ${group.totalImages} ")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF7A5C00)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                                Text(
                                    text = buildString {
                                        append("总条文 ${group.totalRowsCount} 条")
                                        append(" · 总截图 ${group.totalImageCount} 张")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "drawer_bottom_spacer") {
                Box(modifier = Modifier.height(20.dp))
            }
        }
    }
}
