package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.KnowledgeItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * SmartPage 从 [MainPages.kt] 中拆分出来的独立页面组件。
 * 负责展示 AI 文本和本地检索结果，可复用在多个容器中。
 * 拆分后本文件只保留页面状态与列表容器；evidence 渲染见
 * [smartEvidenceSection]、citation 见 [SmartCitationState]、锚点导航见
 * [SmartScrollIndicator]。
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
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
    innerPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
        )
    val coroutineScope = rememberCoroutineScope()
    val citationState = rememberSmartCitationState()
    var showAllEvidence by rememberSaveable(localResults.size) { mutableStateOf(false) }

    val numberedEvidence = remember(localResults) { buildNumberedEvidence(localResults) }
    val evidenceSegments =
        remember(numberedEvidence) {
            buildSmartEvidenceSegments(numberedEvidence)
        }
    val visibleEvidenceSegments =
        remember(evidenceSegments, showAllEvidence) {
            if (showAllEvidence) evidenceSegments else limitSmartEvidenceSegments(evidenceSegments, maxEntries = 4)
        }
    val smartAnchors =
        remember(aiText, thinkingContent, visibleEvidenceSegments) {
            buildSmartAnchors(
                aiText = aiText,
                hasThinkingSection = thinkingContent != null,
                evidenceSegments = visibleEvidenceSegments,
            )
        }
    val citationTargetIndices =
        remember(visibleEvidenceSegments) {
            buildCitationTargetIndices(visibleEvidenceSegments)
        }

    LaunchedEffect(listState, onListPositionChange) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                onListPositionChange(index, offset)
            }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topContentPadding,
                    bottom = 8.dp,
                ),
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
                                citationState.selectedNumber = number
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            },
                            renderMarkdownWhenPossible = answerRenderMarkdown,
                        )
                    }
                    answerFooter?.invoke()
                } else if (showEmptyState && visibleEvidenceSegments.isEmpty() && !isPageLoading) {
                    EmptyState(text = "请输入问题以查看回答")
                }
            }

            smartEvidenceSection(
                visibleSegments = visibleEvidenceSegments,
                totalEntryCount = numberedEvidence.size,
                showAllEvidence = showAllEvidence,
                onToggle = { showAllEvidence = !showAllEvidence },
                highlight = highlight,
                selectedNumber = citationState.selectedNumber,
                metaProvider = metaProvider,
                onOpenDetail = onOpenDetail,
            )
        }

        SmartScrollIndicator(
            listState = listState,
            anchors = smartAnchors,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
        )
    }
}
