package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.component.LazyListScrollAnchor
import com.example.powerai.ui.component.LazyListScrollIndicator
import com.example.powerai.util.PdfSourceRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private data class SmartEvidenceEntry(
    val number: Int,
    val item: KnowledgeItem
)

private data class SmartEvidenceSegment(
    val key: String,
    val fileName: String,
    val entries: List<SmartEvidenceEntry>
)

/**
 * SmartPage 从 [MainPages.kt] 中拆分出来的独立页面组件。
 * 负责展示 AI 文本和本地检索结果，可复用在多个容器中。
 */

@Composable
fun SmartPage(
    aiText: String,
    localResults: List<KnowledgeItem>,
    highlight: String,
    askedAtMillis: Long? = null,
    thinkingContent: (@Composable () -> Unit)? = null,
    answerFooter: (@Composable () -> Unit)? = null,
    answerRenderMarkdown: Boolean = true,
    topContentPadding: Dp = 64.dp,
    metaProvider: ((KnowledgeItem) -> String?)? = null,
    onOpenDetail: (id: Long, blockIndex: Int?, blockId: String?, detailHighlight: String?) -> Unit,
    onRetry: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    showEmptyState: Boolean,
    isPageLoading: Boolean = false,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onListPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset
    )
    val coroutineScope = rememberCoroutineScope()
    var selectedCitation by remember { mutableStateOf<Int?>(null) }
    var showAllEvidence by rememberSaveable(localResults.size) { mutableStateOf(false) }

    val numberedEvidence = remember(localResults) {
        localResults.take(10).mapIndexed { idx, item ->
            SmartEvidenceEntry(
                number = idx + 1,
                item = item.copy(title = "[${idx + 1}] ${item.title}")
            )
        }
    }
    val evidenceSegments = remember(numberedEvidence) {
        buildSmartEvidenceSegments(numberedEvidence)
    }
    val visibleEvidenceSegments = remember(evidenceSegments, showAllEvidence) {
        if (showAllEvidence) evidenceSegments else limitSmartEvidenceSegments(evidenceSegments, maxEntries = 4)
    }
    val smartAnchors = remember(aiText, thinkingContent, visibleEvidenceSegments) {
        buildSmartAnchors(
            aiText = aiText,
            hasThinkingSection = thinkingContent != null,
            evidenceSegments = visibleEvidenceSegments
        )
    }
    val citationTargetIndices = remember(visibleEvidenceSegments) {
        buildCitationTargetIndices(visibleEvidenceSegments)
    }

    LaunchedEffect(selectedCitation) {
        if (selectedCitation != null) {
            delay(1600)
            selectedCitation = null
        }
    }

    LaunchedEffect(listState, onListPositionChange) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                onListPositionChange(index, offset)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = topContentPadding,
                bottom = 8.dp
            )
        ) {
            item {
                if (thinkingContent != null || aiText.isNotBlank()) {
                    thinkingContent?.invoke()
                    if (aiText.isNotBlank()) {
                        TypingAiResponseCard(
                            userMessage = highlight.takeIf { it.isNotBlank() },
                            text = aiText,
                            isLoading = isPageLoading,
                            askedAtMillis = askedAtMillis,
                            onCopy = { onCopy(aiText) },
                            onRetry = onRetry,
                            allowRetry = false,
                            onCitationClick = { number ->
                                val targetIndex = citationTargetIndices[number] ?: return@TypingAiResponseCard
                                selectedCitation = number
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            },
                            renderMarkdownWhenPossible = answerRenderMarkdown
                        )
                    }
                    answerFooter?.invoke()
                } else if (showEmptyState && visibleEvidenceSegments.isEmpty() && !isPageLoading) {
                    EmptyState(text = "请输入问题以查看回答")
                }
            }

            if (visibleEvidenceSegments.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "依据",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                visibleEvidenceSegments.forEach { segment ->
                    item(key = "segment::${segment.key}") {
                        Text(
                            text = segment.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
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
                        }
                    ) { _, entry ->
                        val item = entry.item
                        KnowledgeItemCard(
                            item = item,
                            highlight = highlight,
                            metaLine = metaProvider?.invoke(item) ?: buildString {
                                append(PdfSourceRef.userVisibleSource(item.source))
                                item.pageNumber?.let { append(" · ${it}") }
                            },
                            isSelected = selectedCitation == entry.number,
                            onClick = {
                                if (item.id > 0) onOpenDetail(
                                    item.id,
                                    item.hitBlockIndex,
                                    item.hitBlockId,
                                    item.highlightHint?.takeIf { it.isNotBlank() } ?: highlight
                                )
                            },
                            expanded = false,
                            onExpand = null,
                            previewMaxLines = 3
                        )
                    }
                }
                if (numberedEvidence.size > 4) {
                    item(key = "evidence_toggle") {
                        CompactEvidenceButton(
                            expanded = showAllEvidence,
                            totalCount = numberedEvidence.size,
                            onToggle = { showAllEvidence = !showAllEvidence }
                        )
                    }
                }
            }
        }

        LazyListScrollIndicator(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            anchors = smartAnchors,
            bubbleLabel = { currentIndex, _, currentAnchor ->
                buildSmartBubbleLabel(
                    currentIndex = currentIndex,
                    currentAnchor = currentAnchor,
                    anchors = smartAnchors
                )
            },
            topPadding = 96.dp,
            bottomPadding = 20.dp
        )
    }
}

private fun buildSmartEvidenceSegments(
    entries: List<SmartEvidenceEntry>
): List<SmartEvidenceSegment> {
    if (entries.isEmpty()) return emptyList()

    val segments = mutableListOf<SmartEvidenceSegment>()
    var currentFileName: String? = null
    var currentItems = mutableListOf<SmartEvidenceEntry>()
    var segmentIndex = 0

    fun flush() {
        val fileName = currentFileName ?: return
        segments += SmartEvidenceSegment(
            key = "seg-$segmentIndex-${fileName.hashCode()}",
            fileName = fileName,
            entries = currentItems.toList()
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

private fun buildSmartAnchors(
    aiText: String,
    hasThinkingSection: Boolean,
    evidenceSegments: List<SmartEvidenceSegment>
): List<LazyListScrollAnchor> {
    if (aiText.isBlank() && !hasThinkingSection && evidenceSegments.isEmpty()) return emptyList()

    var itemIndex = 0
    return buildList {
        add(
            LazyListScrollAnchor(
                itemIndex = itemIndex,
                label = if (aiText.isNotBlank()) "回答" else "思",
                fullLabel = when {
                    aiText.isNotBlank() -> "智能回答"
                    hasThinkingSection -> "思考中"
                    else -> "结果顶部"
                }
            )
        )
        itemIndex += 1

        if (evidenceSegments.isNotEmpty()) {
            add(
                LazyListScrollAnchor(
                    itemIndex = itemIndex,
                    label = "证据",
                    fullLabel = "参考资"
                )
            )
            itemIndex += 1

            evidenceSegments.forEach { segment ->
                add(
                    LazyListScrollAnchor(
                        itemIndex = itemIndex,
                        label = smartShortLabel(segment.fileName),
                        fullLabel = segment.fileName
                    )
                )
                itemIndex += 1 + segment.entries.size
            }
        }
    }
}

private fun buildCitationTargetIndices(
    evidenceSegments: List<SmartEvidenceSegment>
): Map<Int, Int> {
    if (evidenceSegments.isEmpty()) return emptyMap()

    var itemIndex = 2
    return buildMap {
        evidenceSegments.forEach { segment ->
            itemIndex += 1
            segment.entries.forEach { entry ->
                put(entry.number, itemIndex)
                itemIndex += 1
            }
        }
    }
}

private fun limitSmartEvidenceSegments(
    segments: List<SmartEvidenceSegment>,
    maxEntries: Int
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

private fun buildSmartBubbleLabel(
    currentIndex: Int,
    currentAnchor: LazyListScrollAnchor?,
    anchors: List<LazyListScrollAnchor>
): String {
    val anchor = currentAnchor ?: return "${currentIndex + 1}"
    if (anchor.fullLabel == "智能回答" || anchor.fullLabel == "结果顶部") return anchor.fullLabel
    if (anchor.fullLabel == "参考资") return anchor.fullLabel

    val relativeIndex = (currentIndex - anchor.itemIndex).coerceAtLeast(0)
    val nextAnchorIndex = anchors
        .firstOrNull { it.itemIndex > anchor.itemIndex }
        ?.itemIndex
        ?: Int.MAX_VALUE
    return when {
        relativeIndex <= 0 -> anchor.fullLabel
        currentIndex >= nextAnchorIndex -> anchor.fullLabel
        else -> "${anchor.fullLabel} · ${relativeIndex}条证"
    }
}

private fun smartEvidenceFileName(item: KnowledgeItem): String {
    return PdfSourceRef.parse(item.source)?.fileName
        ?: PdfSourceRef.userVisibleSource(item.source).ifBlank { "参考资" }
}

@Composable
private fun CompactEvidenceButton(
    expanded: Boolean,
    totalCount: Int,
    onToggle: () -> Unit
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (expanded) "收起资料" else "展开全部资料 ($totalCount)",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun smartShortLabel(fileName: String): String {
    val safeName = fileName.ifBlank { "参考资" }
    return if (safeName.length <= 14) safeName else safeName.take(14) + "..."
}
