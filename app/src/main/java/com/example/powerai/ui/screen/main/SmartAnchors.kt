package com.example.powerai.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.LazyListScrollAnchor
import com.example.powerai.ui.component.LazyListScrollIndicator

internal fun buildSmartAnchors(
    aiText: String,
    hasThinkingSection: Boolean,
    evidenceSegments: List<SmartEvidenceSegment>,
): List<LazyListScrollAnchor> {
    if (aiText.isBlank() && !hasThinkingSection && evidenceSegments.isEmpty()) return emptyList()

    var itemIndex = 0
    return buildList {
        add(
            LazyListScrollAnchor(
                itemIndex = itemIndex,
                label = if (aiText.isNotBlank()) "回答" else "思",
                fullLabel =
                    when {
                        aiText.isNotBlank() -> "智能回答"
                        hasThinkingSection -> "思考中"
                        else -> "结果顶部"
                    },
            ),
        )
        itemIndex += 1

        if (evidenceSegments.isNotEmpty()) {
            add(
                LazyListScrollAnchor(
                    itemIndex = itemIndex,
                    label = "证据",
                    fullLabel = "参考资",
                ),
            )
            itemIndex += 1

            evidenceSegments.forEach { segment ->
                add(
                    LazyListScrollAnchor(
                        itemIndex = itemIndex,
                        label = smartShortLabel(segment.fileName),
                        fullLabel = segment.fileName,
                    ),
                )
                itemIndex += 1 + segment.entries.size
            }
        }
    }
}

@Suppress("ReturnCount")
internal fun buildSmartBubbleLabel(
    currentIndex: Int,
    currentAnchor: LazyListScrollAnchor?,
    anchors: List<LazyListScrollAnchor>,
): String {
    val anchor = currentAnchor ?: return "${currentIndex + 1}"
    if (anchor.fullLabel == "智能回答" || anchor.fullLabel == "结果顶部") return anchor.fullLabel
    if (anchor.fullLabel == "参考资") return anchor.fullLabel

    val relativeIndex = (currentIndex - anchor.itemIndex).coerceAtLeast(0)
    val nextAnchorIndex =
        anchors
            .firstOrNull { it.itemIndex > anchor.itemIndex }
            ?.itemIndex
            ?: Int.MAX_VALUE
    return when {
        relativeIndex <= 0 -> anchor.fullLabel
        currentIndex >= nextAnchorIndex -> anchor.fullLabel
        else -> "${anchor.fullLabel} · ${relativeIndex}条证"
    }
}

internal fun smartShortLabel(fileName: String): String {
    val safeName = fileName.ifBlank { "参考资" }
    return if (safeName.length <= ANCHOR_LABEL_MAX_LENGTH) {
        safeName
    } else {
        safeName.take(ANCHOR_LABEL_MAX_LENGTH) + "..."
    }
}

@Suppress("MagicNumber")
private const val ANCHOR_LABEL_MAX_LENGTH = 14

/**
 * Anchor navigation overlay for [SmartPage]: the right-edge scroll indicator
 * with per-section bubble labels.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SmartScrollIndicator(
    listState: androidx.compose.foundation.lazy.LazyListState,
    anchors: List<LazyListScrollAnchor>,
    modifier: Modifier = Modifier,
) {
    LazyListScrollIndicator(
        listState = listState,
        modifier = modifier,
        anchors = anchors,
        bubbleLabel = { currentIndex, _, currentAnchor ->
            buildSmartBubbleLabel(
                currentIndex = currentIndex,
                currentAnchor = currentAnchor,
                anchors = anchors,
            )
        },
        topPadding = 96.dp,
        bottomPadding = 20.dp,
    )
}
