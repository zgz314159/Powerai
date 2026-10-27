package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.chat.ChatSession

@Composable
internal fun AiHistoryDrawerContent(
    sessions: List<ChatSession>,
    selectedSessionId: Long?,
    onNewSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onEdgeAction: (() -> Unit)? = null
) {
    DrawerWrapper(
        title = "AI 聊天历史",
        actionLabel = "开启新会话",
        onAction = onNewSession,
        onEdgeAction = onEdgeAction
    ) {
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无聊天历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@DrawerWrapper
        }

        DrawerSummaryCard(
            title = "AI 会话目录",
            subtitle = "${sessions.size} 个会话；点击可切换上下文并继续对话"
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                DrawerSectionLabel(label = "最近会")
            }

            items(sessions, key = { it.id }) { session ->
                val lastTurn = session.turns.lastOrNull()
                val subtitle = lastTurn?.question?.take(40)?.let { "问：$it" } ?: "新会"
                val isSelected = session.id == selectedSessionId

                DrawerDirectoryCard(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = session.title.ifBlank { "未命名会" },
                    subtitle = subtitle,
                    footer = if (isSelected) "当前正在此会话中" else "点击切换到此会话",
                    accentColor = Color(0xFF6C5B7B),
                    isSelected = isSelected,
                    onClick = { onSelectSession(session.id) }
                )
            }

            item(key = "ai_drawer_bottom_spacer") {
                Box(modifier = Modifier.height(20.dp))
            }
        }
    }
}
