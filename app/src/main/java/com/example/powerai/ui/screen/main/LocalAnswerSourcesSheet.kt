@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.powerai.util.PdfSourceRef

@Composable
internal fun LocalAnswerSourcesSheet(
    evidenceItems: List<KnowledgeItem>,
    onDismiss: () -> Unit,
    sourceFileNameProvider: (KnowledgeItem) -> String,
    onOpenSource: (KnowledgeItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "回答来源",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Text(
                text = "[1] [2] [3] 对应的是回答引用到的来源文件名。点击后会跳转到数据库页并定位到该文件里的对应词条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(evidenceItems.take(10), key = { index, item -> "source::$index::${item.id}" }) { index, item ->
                    LocalAnswerSourceRow(
                        number = index + 1,
                        item = item,
                        sourceFileNameProvider = sourceFileNameProvider,
                        onOpenSource = onOpenSource
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalAnswerSourceRow(
    number: Int,
    item: KnowledgeItem,
    sourceFileNameProvider: (KnowledgeItem) -> String,
    onOpenSource: (KnowledgeItem) -> Unit
) {
    val fileName = sourceFileNameProvider(item)
    val metaLine = buildString {
        item.pageNumber?.let { append("${it}") }
        item.hitBlockIndex?.let {
            if (isNotBlank()) append(" · ")
            append(formatHitBlockLabel(it))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSource(item) }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "[$number]",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (metaLine.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun formatHitBlockLabel(hitBlockIndex: Int): String {
    val displayIndex = (hitBlockIndex + 1).coerceAtLeast(1)
    return "命中${displayIndex}"
}

internal fun resolveLocalSourceFileName(
    item: KnowledgeItem,
    databaseFileName: String?
): String {
    databaseFileName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

    PdfSourceRef.parse(item.source)?.fileName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val sourceParts = listOfNotNull(item.contextLabel, item.source)
        .flatMap { value -> value.split(Regex("\\s*[·•|｜]\\s*")) }
        .map { it.trim() }
        .filter { it.isNotBlank() }

    sourceParts.firstOrNull(::looksLikeUserVisibleFileName)?.let { return it }

    PdfSourceRef.userVisibleSource(item.source)
        .takeIf { it.isNotBlank() }
        ?.let { return it }

    return "参考资"
}

private fun looksLikeUserVisibleFileName(value: String): Boolean {
    val cleaned = value.trim()
    if (cleaned.isBlank()) return false
    if (cleaned.startsWith("来源") && cleaned.contains("摘录")) return false
    if (cleaned.startsWith("命中")) return false
    if (cleaned == "参考资") return false
    return true
}
