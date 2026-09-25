package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.LocalSearchEntry

@Composable
internal fun LocalHistoryDrawerContent(
    history: List<LocalSearchEntry>,
    onSelectQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    onEdgeAction: (() -> Unit)? = null
) {
    DrawerWrapper(
        title = "本地搜索历史",
        actionLabel = "清除历史",
        onAction = onClearHistory,
        onEdgeAction = onEdgeAction
    ) {
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无本地搜索历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@DrawerWrapper
        }

        val groups = remember(history) {
            history.groupBy { entry ->
                java.time.Instant.ofEpochMilli(entry.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }.toSortedMap(compareByDescending { it })
        }

        DrawerSummaryCard(
            title = "本地检索历史",
            subtitle = "${history.size} 条本地检索历史"
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (date, entries) ->
                item {
                    val label = runCatching {
                        val today = java.time.LocalDate.now()
                        val yesterday = today.minusDays(1)
                        when (date) {
                            today -> "今天"
                            yesterday -> "昨天"
                            else -> date.toString()
                        }
                    }.getOrDefault(date.toString())
                    DrawerSectionLabel(label = label)
                }

                items(entries, key = { entry -> "${entry.timestamp}-${entry.query}" }) { entry ->
                    val timeLabel = runCatching {
                        java.time.Instant.ofEpochMilli(entry.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                            .withSecond(0)
                            .withNano(0)
                            .toString()
                    }.getOrDefault("未知时间")

                    DrawerDirectoryCard(
                        icon = Icons.Outlined.History,
                        title = entry.query,
                        subtitle = "本地检索 · $timeLabel",
                        footer = "点击重新执行这次本地检",
                        isSelected = false,
                        accentColor = Color(0xFFC06C84),
                        onClick = { onSelectQuery(entry.query) }
                    )
                }
            }

            item(key = "local_drawer_bottom_spacer") {
                Box(modifier = Modifier.height(20.dp))
            }
        }
    }
}
