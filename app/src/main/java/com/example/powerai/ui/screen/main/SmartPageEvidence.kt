package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.util.PdfSourceRef

internal data class SmartEvidenceEntry(
    val number: Int,
    val item: KnowledgeItem,
)

internal data class SmartEvidenceSegment(
    val key: String,
    val fileName: String,
    val entries: List<SmartEvidenceEntry>,
)

@Suppress("MagicNumber")
private object SmartEvidenceLimits {
    const val PREVIEW_LIMIT = 10
    const val COLLAPSE_THRESHOLD = 4
}

internal fun buildNumberedEvidence(localResults: List<KnowledgeItem>): List<SmartEvidenceEntry> =
    localResults.take(SmartEvidenceLimits.PREVIEW_LIMIT).mapIndexed { idx, item ->
        SmartEvidenceEntry(
            number = idx + 1,
            item = item.copy(title = "[${idx + 1}] ${item.title}"),
        )
    }

/**
 * Evidence list renderer for [SmartPage]: numbered entries grouped into
 * per-file segments with the "依据" header, cards and expand toggle. A
 * [LazyListScope] extension so list keys and virtualization stay identical
 * to the original inline block.
 */
@Suppress("LongParameterList")
internal fun LazyListScope.smartEvidenceSection(
    visibleSegments: List<SmartEvidenceSegment>,
    totalEntryCount: Int,
    showAllEvidence: Boolean,
    onToggle: () -> Unit,
    highlight: String,
    selectedNumber: Int?,
    metaProvider: ((KnowledgeItem) -> String?)?,
    onOpenDetail: (id: Long, blockIndex: Int?, blockId: String?, detailHighlight: String?) -> Unit,
) {
    if (visibleSegments.isEmpty()) return

    item {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "依据",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
    visibleSegments.forEach { segment ->
        item(key = "segment::${segment.key}") {
            Text(
                text = segment.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        itemsIndexed(
            items = segment.entries,
            key = { index, entry ->
                val item = entry.item
                if (item.id > 0L) {
                    "kid:${item.id}"
                } else {
                    "seg:${segment.key}|idx:$index|src:${item.source}|title:${item.title.hashCode()}|content:${item.content.hashCode()}"
                }
            },
        ) { _, entry ->
            val item = entry.item
            KnowledgeItemCard(
                item = item,
                highlight = highlight,
                metaLine =
                    metaProvider?.invoke(item) ?: buildString {
                        append(PdfSourceRef.userVisibleSource(item.source))
                        item.pageNumber?.let { append(" · $it") }
                    },
                isSelected = selectedNumber == entry.number,
                onClick = {
                    if (item.id > 0) {
                        onOpenDetail(
                            item.id,
                            item.hitBlockIndex,
                            item.hitBlockId,
                            item.highlightHint?.takeIf { it.isNotBlank() } ?: highlight,
                        )
                    }
                },
                expanded = false,
                onExpand = null,
                previewMaxLines = 3,
            )
        }
    }
    if (totalEntryCount > SmartEvidenceLimits.COLLAPSE_THRESHOLD) {
        item(key = "evidence_toggle") {
            CompactEvidenceButton(
                expanded = showAllEvidence,
                totalCount = totalEntryCount,
                onToggle = onToggle,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CompactEvidenceButton(
    expanded: Boolean,
    totalCount: Int,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (expanded) "收起资料" else "展开全部资料 ($totalCount)",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

internal fun buildSmartEvidenceSegments(entries: List<SmartEvidenceEntry>): List<SmartEvidenceSegment> {
    if (entries.isEmpty()) return emptyList()

    val segments = mutableListOf<SmartEvidenceSegment>()
    var currentFileName: String? = null
    var currentItems = mutableListOf<SmartEvidenceEntry>()
    var segmentIndex = 0

    fun flush() {
        val fileName = currentFileName ?: return
        segments +=
            SmartEvidenceSegment(
                key = "seg-$segmentIndex-${fileName.hashCode()}",
                fileName = fileName,
                entries = currentItems.toList(),
            )
        segmentIndex += 1
    }

    entries.forEach { entry ->
        val fileName = smartEvidenceFileName(entry.item)
        if (currentFileName == null) {
            currentFileName = fileName
            currentItems.add(entry)
        } else if (currentFileName == fileName) {
            currentItems.add(entry)
        } else {
            flush()
            currentFileName = fileName
            currentItems = mutableListOf(entry)
        }
    }
    flush()
    return segments
}

internal fun limitSmartEvidenceSegments(
    segments: List<SmartEvidenceSegment>,
    maxEntries: Int,
): List<SmartEvidenceSegment> {
    if (segments.isEmpty() || maxEntries <= 0) return emptyList()

    var remaining = maxEntries
    return buildList {
        segments.forEach { segment ->
            if (remaining <= 0) return@forEach
            val visibleEntries = segment.entries.take(remaining)
            if (visibleEntries.isNotEmpty()) {
                add(segment.copy(entries = visibleEntries))
                remaining -= visibleEntries.size
            }
        }
    }
}

internal fun smartEvidenceFileName(item: KnowledgeItem): String {
    return PdfSourceRef.parse(item.source)?.fileName
        ?: PdfSourceRef.userVisibleSource(item.source).ifBlank { "参考资" }
}
