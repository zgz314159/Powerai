package com.example.powerai.ui.screen.main

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.navigation.Screen
import com.example.powerai.ui.screen.hybrid.AiUiState
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun MainPagerTabsAndPages(
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    isPagerScrolling: Boolean,
    isPageChanging: Boolean,
    effectiveHighlight: String,
    uiState: HybridViewModel.UiStateWithImport,
    localResults: List<KnowledgeItem>,
    metaProvider: ((KnowledgeItem) -> String?)? = null,
    expandedItemId: Long?,
    onToggleExpand: (Long) -> Unit,
    currentPage: Int,
    totalPages: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    aiUiState: AiUiState,
    aiResult: String,
    onRetryAi: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenDetailRoute: (route: String) -> Unit,
    onOpenAiDetail: (title: String, content: String, query: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("本地", "AI", "智能")
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = Color.Transparent
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(title, style = MaterialTheme.typography.titleSmall) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = true,
        beyondViewportPageCount = 2,
        key = { DisplayMode.values()[it].name }
    ) { pageIndex ->
        val currentMode = DisplayMode.values()[pageIndex]
        when (currentMode) {
            DisplayMode.LOCAL -> {
                LocalResultsPage(
                    localResults = localResults,
                    expandedItemId = expandedItemId,
                    onToggleExpand = onToggleExpand,
                    highlight = effectiveHighlight,
                    metaProvider = metaProvider,
                    onOpenDetail = { id, blockIndex, blockId ->
                        val encoded = Uri.encode(effectiveHighlight)
                        onOpenDetailRoute(Screen.Detail.createRoute(id, encoded, blockIndex, blockId))
                    },
                    currentPage = currentPage,
                    totalPages = totalPages,
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onPrev = onPrev,
                    onNext = onNext,
                    showEmptyState = uiState.question.isNotBlank(),
                    animateItems = !isPagerScrolling && !isPageChanging,
                    isPageLoading = isPageChanging
                )
                Log.d(
                    "MainScreen",
                    "Rendered LOCAL page=$currentPage animateItems=${!isPagerScrolling && !isPageChanging} isPagerScrolling=$isPagerScrolling isPageChanging=$isPageChanging localSize=${localResults.size}"
                )
            }
            DisplayMode.AI -> {
                AiResponsePage(
                    aiUiState = aiUiState,
                    aiText = aiResult,
                    onRetry = onRetryAi,
                    onCopy = onCopyToClipboard,
                    highlight = effectiveHighlight,
                    references = emptyList(),
                    onOpenEvidenceDetail = null,
                    showEmptyState = uiState.question.isNotBlank()
                )
            }
            DisplayMode.SMART -> {
                SmartPage(
                    aiText = aiResult,
                    localResults = localResults,
                    expandedItemId = expandedItemId,
                    onToggleExpand = onToggleExpand,
                    highlight = effectiveHighlight,
                    metaProvider = metaProvider,
                    onOpenDetail = { id, blockIndex, blockId ->
                        val encoded = Uri.encode(effectiveHighlight)
                        onOpenDetailRoute(Screen.Detail.createRoute(id, encoded, blockIndex, blockId))
                    },
                    onOpenAiDetail = { title, content ->
                        onOpenAiDetail(title, content, effectiveHighlight)
                    },
                    onRetry = onRetryAi,
                    onCopy = onCopyToClipboard,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onPrev = onPrev,
                    onNext = onNext,
                    showEmptyState = uiState.question.isNotBlank(),
                    animateItems = !isPagerScrolling && !isPageChanging,
                    isPageLoading = isPageChanging
                )
                Log.d(
                    "MainScreen",
                    "Rendered SMART page=$currentPage animateItems=${!isPagerScrolling && !isPageChanging} isPagerScrolling=$isPagerScrolling isPageChanging=$isPageChanging localSize=${localResults.size}"
                )
            }
        }
    }
}
