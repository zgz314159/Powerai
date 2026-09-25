package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.screen.hybrid.AiUiState
import com.example.powerai.util.PdfSourceRef

/**
 * `MainPages.kt` 拆出的组件
 */
@Composable
fun AiResponsePage(
    aiUiState: AiUiState,
    aiText: String,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    highlight: String = "",
    askedAtMillis: Long? = null,
    sources: List<String> = emptyList(),
    references: List<KnowledgeItem> = emptyList(),
    onOpenEvidenceDetail: ((id: Long, blockIndex: Int?, blockId: String?) -> Unit)? = null,
    showEmptyState: Boolean
) {
    val displayText = remember(aiText) {
        // Keep AI page "无引"：仅用于展示时去"[n] 标记，SMART 页保留原文以支持点击跳证据"
        aiText.replace(Regex("\\[(\\d{1,3})]"), "").replace(Regex("\\s{2,}"), " ").trim()
    }

    val numberedEvidence = remember(references) {
        references.take(10).mapIndexed { idx, item ->
            item.copy(title = "[${idx + 1}] ${item.title}")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            when (aiUiState) {
                is AiUiState.Idle -> {
                    if (showEmptyState) {
                        EmptyState(text = "请输入问题以获取 AI 答案")
                    }
                }
                is AiUiState.Error -> {
                    TypingAiResponseCard(
                        userMessage = highlight.takeIf { it.isNotBlank() },
                        text = aiUiState.message,
                        isLoading = false,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(aiUiState.message) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
                is AiUiState.Loading -> {
                    TypingAiResponseCard(
                        userMessage = highlight.takeIf { it.isNotBlank() },
                        text = displayText,
                        isLoading = true,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(displayText) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
                is AiUiState.Success -> {
                    TypingAiResponseCard(
                        userMessage = highlight.takeIf { it.isNotBlank() },
                        text = displayText,
                        isLoading = false,
                        askedAtMillis = askedAtMillis,
                        sources = sources,
                        onCopy = { onCopy(displayText) },
                        onRetry = onRetry,
                        allowRetry = false
                    )
                }
            }
        }

        if (numberedEvidence.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "引用证据", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
            }
            itemsIndexed(
                items = numberedEvidence,
                key = { _, it -> it.id }
            ) { _, item ->
                KnowledgeItemCard(
                    item = item,
                    highlight = highlight,
                    metaLine = buildString {
                        append(item.source)
                        item.pageNumber?.let { append(" · ${it}") }
                        item.hitBlockIndex?.let { append(" · 命中it") }
                    },
                    onClick = if (onOpenEvidenceDetail != null) {
                        {
                            if (item.id > 0) onOpenEvidenceDetail(item.id, item.hitBlockIndex, item.hitBlockId)
                        }
                    } else {
                        null
                    },
                    expanded = false,
                    onExpand = null,
                    previewMaxLines = 5
                )
            }
        }
    }
}
