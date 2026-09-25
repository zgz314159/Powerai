package com.example.powerai.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

data class LazyListScrollAnchor(
    val itemIndex: Int,
    val label: String,
    val fullLabel: String = label
)

private data class ScrollIndicatorMetrics(
    val canScroll: Boolean,
    val progress: Float,
    val visibleFraction: Float
)

private data class ScrollIndicatorBubble(
    val text: String,
    val centerYPx: Float
)

@Composable
fun LazyListScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    touchWidth: Dp = 18.dp,
    minThumbHeight: Dp = 36.dp,
    topPadding: Dp = 96.dp,
    bottomPadding: Dp = 16.dp,
    anchors: List<LazyListScrollAnchor> = emptyList(),
    bubbleLabel: ((currentItemIndex: Int, totalItems: Int, currentAnchor: LazyListScrollAnchor?) -> String)? = null
) {
    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val topPaddingPx = with(density) { topPadding.toPx() }
    val bottomPaddingPx = with(density) { bottomPadding.toPx() }
    val coroutineScope = rememberCoroutineScope()
    var bubble by remember { mutableStateOf<ScrollIndicatorBubble?>(null) }
    val metrics by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= 0 || visibleItems.isEmpty()) {
                return@derivedStateOf ScrollIndicatorMetrics(false, 0f, 1f)
            }

            val viewportSize = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
                .toFloat()
            val averageItemSize = visibleItems
                .map { it.size }
                .average()
                .takeIf { it > 0.0 }
                ?.toFloat()
                ?: viewportSize
            val estimatedVisibleItems = max(viewportSize / averageItemSize, 1f)
            val visibleFraction = (estimatedVisibleItems / totalItems.toFloat()).coerceIn(0.08f, 1f)
            val scrolledItems = listState.firstVisibleItemIndex.toFloat() +
                (listState.firstVisibleItemScrollOffset / averageItemSize)
            val maxScrollableItems = (totalItems.toFloat() - estimatedVisibleItems).coerceAtLeast(1f)
            val progress = (scrolledItems / maxScrollableItems).coerceIn(0f, 1f)

            ScrollIndicatorMetrics(
                canScroll = totalItems > estimatedVisibleItems + 0.5f,
                progress = progress,
                visibleFraction = visibleFraction
            )
        }
    }

    if (!metrics.canScroll) return

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    val anchorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val bubbleShape = RoundedCornerShape(12.dp)
    val orderedAnchors = remember(anchors) { anchors.sortedBy { it.itemIndex } }

    BoxWithConstraints(modifier = modifier) {
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val trackHeightPx = (fullHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)
        val bubbleVerticalPaddingPx = with(density) { 16.dp.toPx() }
        val bubbleXOffsetPx = with(density) { (touchWidth + 12.dp).roundToPx() }
        val anchorFractions = remember(orderedAnchors, listState.layoutInfo.totalItemsCount) {
            val totalItems = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            orderedAnchors.map { anchor ->
                val fraction = if (totalItems <= 1) 0f else {
                    anchor.itemIndex.toFloat() / (totalItems - 1).toFloat()
                }
                anchor to fraction.coerceIn(0f, 1f)
            }
        }

        fun anchorForIndex(index: Int): LazyListScrollAnchor? {
            return orderedAnchors.lastOrNull { it.itemIndex <= index }
                ?: orderedAnchors.firstOrNull()
        }

        fun buildBubbleLabel(
            currentIndex: Int,
            totalItems: Int,
            currentAnchor: LazyListScrollAnchor?
        ): String {
            return bubbleLabel?.invoke(currentIndex, totalItems, currentAnchor)
                ?: buildString {
                    currentAnchor?.label
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            append(currentAnchor.fullLabel)
                            append(" · ")
                        }
                    append(currentIndex.coerceAtLeast(0) + 1)
                    append("/")
                    append(totalItems.coerceAtLeast(1))
                }
        }

        fun showBubble(currentIndex: Int, fraction: Float) {
            val totalItems = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            val currentAnchor = anchorForIndex(currentIndex)
            val text = buildBubbleLabel(currentIndex, totalItems, currentAnchor)
            val centerY = topPaddingPx + trackHeightPx * fraction.coerceIn(0f, 1f)
            bubble = ScrollIndicatorBubble(text = text, centerYPx = centerY)
        }

        fun hideBubbleSoon() {
            coroutineScope.launch {
                delay(900)
                bubble = null
            }
        }

        fun anchorSnapIndex(rawFraction: Float): Int? {
            if (anchorFractions.isEmpty()) return null
            val nearest = anchorFractions.minByOrNull { (_, fraction) ->
                kotlin.math.abs(fraction - rawFraction)
            } ?: return null
            val snapThreshold = 0.035f
            return if (kotlin.math.abs(nearest.second - rawFraction) <= snapThreshold) {
                nearest.first.itemIndex
            } else {
                null
            }
        }

        fun scrollToFraction(rawFraction: Float, preferAnchorSnap: Boolean): Int {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= 0 || visibleItems.isEmpty()) return 0

            val fraction = rawFraction.coerceIn(0f, 1f)
            val snappedIndex = if (preferAnchorSnap) anchorSnapIndex(fraction) else null
            if (snappedIndex != null) {
                coroutineScope.launch {
                    listState.scrollToItem(snappedIndex.coerceIn(0, totalItems - 1))
                }
                return snappedIndex
            }

            val viewportSize = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
                .toFloat()
            val averageItemSize = visibleItems
                .map { it.size }
                .average()
                .takeIf { it > 0.0 }
                ?.toFloat()
                ?: viewportSize
            val estimatedVisibleItems = max(viewportSize / averageItemSize, 1f)
            val maxScrollableItems = (totalItems.toFloat() - estimatedVisibleItems).coerceAtLeast(0f)
            val targetScrolledItems = fraction * maxScrollableItems
            val targetIndex = targetScrolledItems.toInt().coerceIn(0, (totalItems - 1).coerceAtLeast(0))
            val targetOffset = ((targetScrolledItems - targetIndex) * averageItemSize)
                .roundToInt()
                .coerceAtLeast(0)

            coroutineScope.launch {
                listState.scrollToItem(targetIndex, targetOffset)
            }

            return targetIndex
        }

        bubble?.let { bubbleState ->
            val bubbleHeightPx = with(density) { 34.dp.toPx() }
            val targetYPx = (bubbleState.centerYPx - bubbleHeightPx / 2f)
                .coerceIn(bubbleVerticalPaddingPx, (fullHeightPx - bubbleHeightPx - bubbleVerticalPaddingPx).coerceAtLeast(0f))
            Text(
                text = bubbleState.text,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -bubbleXOffsetPx,
                            y = targetYPx.roundToInt()
                        )
                    }
                    .clip(bubbleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .width(touchWidth)
                .fillMaxHeight()
                .pointerInput(listState, trackHeightPx, topPaddingPx, bottomPaddingPx, orderedAnchors) {
                    detectTapGestures { offset ->
                        val trackY = ((offset.y - topPaddingPx) / trackHeightPx).coerceIn(0f, 1f)
                        val targetIndex = scrollToFraction(trackY, preferAnchorSnap = true)
                        showBubble(targetIndex, trackY)
                        hideBubbleSoon()
                    }
                }
                .pointerInput(listState, trackHeightPx, topPaddingPx, bottomPaddingPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val trackY = ((offset.y - topPaddingPx) / trackHeightPx).coerceIn(0f, 1f)
                            val targetIndex = scrollToFraction(trackY, preferAnchorSnap = true)
                            showBubble(targetIndex, trackY)
                        },
                        onVerticalDrag = { change, _ ->
                            val trackY = ((change.position.y - topPaddingPx) / trackHeightPx).coerceIn(0f, 1f)
                            val targetIndex = scrollToFraction(trackY, preferAnchorSnap = true)
                            showBubble(targetIndex, trackY)
                            change.consume()
                        },
                        onDragEnd = {
                            hideBubbleSoon()
                        },
                        onDragCancel = {
                            bubble = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = with(density) { thickness.toPx() }
                val left = ((size.width - trackWidth) / 2f).coerceAtLeast(0f)
                val top = topPaddingPx.coerceAtMost(size.height)
                val bottom = (size.height - bottomPaddingPx).coerceAtLeast(top)
                val trackHeight = (bottom - top).coerceAtLeast(1f)
                val thumbHeight = (trackHeight * metrics.visibleFraction)
                    .coerceAtLeast(minThumbHeightPx)
                    .coerceAtMost(trackHeight)
                val thumbTop = top + (trackHeight - thumbHeight) * metrics.progress
                val radius = CornerRadius(trackWidth / 2f, trackWidth / 2f)

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, top),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = radius
                )
                anchorFractions.forEach { (_, fraction) ->
                    val anchorCenterY = top + trackHeight * fraction
                    drawCircle(
                        color = anchorColor,
                        radius = trackWidth,
                        center = Offset(left + trackWidth / 2f, anchorCenterY)
                    )
                }
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(left, thumbTop),
                    size = Size(trackWidth, thumbHeight),
                    cornerRadius = radius
                )
            }
        }
    }
}
