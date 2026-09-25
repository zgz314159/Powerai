package com.example.powerai.ui.screen.main
import com.example.powerai.core.model.SmartProgressPhase

import com.example.powerai.core.model.KnowledgeItem

import android.net.Uri
import com.example.powerai.BuildConfig
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.feature.searchchat.*
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.domain.model.LocalSearchEntry
import com.example.powerai.util.PdfSourceRef
import kotlinx.coroutines.launch

private const val SMART_DEEPSEEK_DEVICE_PATH = "/storage/emulated/0/Android/data/com.example.powerai/files"

@Composable
internal fun SmartDeepSeekTabContent(
    navController: NavHostController,
    hybridViewModel: HybridViewModel,
    drawerState: DrawerState,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    innerPadding: PaddingValues = PaddingValues(),
    deepSeekViewModel: DeepSeekViewModel = hiltViewModel()
) {
    val deepState by deepSeekViewModel.uiState.collectAsState()
    val hybridState by hybridViewModel.uiState.collectAsState()
    val smartHistory = hybridState.smartSearchHistory
    val smartScrollIndex = hybridState.smartScrollIndex
    val smartScrollOffset = hybridState.smartScrollOffset

    val localCoroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var showAdvancedControls by rememberSaveable(showSearchBar) { mutableStateOf(false) }

    val metaProvider: ((com.example.powerai.core.model.KnowledgeItem) -> String?) = remember {
        { item -> buildString { append(PdfSourceRef.userVisibleSource(item.source)); item.pageNumber?.let { append(" · ${it}") } } }
    }

    fun submitSmartQuery(rawQuery: String) {
        val normalized = rawQuery.trim()
        if (normalized.isBlank()) return
        if (normalized != rawQuery) onQueryChange(normalized)
        hybridViewModel.recordSmartSearchQuery(normalized)
        deepSeekViewModel.onIntent(DeepSeekIntent.GenerateGroundedAnswer(normalized, deepState.answerMode.maxTokens))
        onShowSearchBarChange(false)
    }

    val showThinkingPreview = deepState.answerMode.thinkingEnabled && deepState.thinkingPreview.isNotBlank()
    val shouldShowProgressCard = deepState.isLoading || showThinkingPreview || deepState.progressState.phase != SmartProgressPhase.IDLE

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.18f),
        drawerContent = {
            SmartHistoryDrawerContent(
                history = smartHistory,
                onSelectQuery = { query ->
                    val normalized = query.trim()
                    if (normalized.isBlank()) {
                        localCoroutineScope.launch { drawerState.close() }
                        return@SmartHistoryDrawerContent
                    }
                    onQueryChange(normalized)
                    submitSmartQuery(normalized)
                    localCoroutineScope.launch { drawerState.close() }
                },
                onClearHistory = {
                    hybridViewModel.clearSmartSearchHistory()
                    localCoroutineScope.launch { drawerState.close() }
                },
                onEdgeAction = { localCoroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelMaxHeight = maxHeight * 0.78f
            SmartPage(
                aiText = deepState.result,
                localResults = deepState.smartReferences,
                highlight = searchQuery,
                askedAtMillis = deepState.askedAtMillis,
                thinkingContent = if (shouldShowProgressCard) {
                    {
                        SmartDeepSeekProgressCard(
                            progressState = deepState.progressState,
                            askedAtMillis = deepState.askedAtMillis,
                            thinkingPreview = deepState.thinkingPreview,
                            showAbort = deepState.isLoading,
                            onAbort = deepSeekViewModel::abortGeneration,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else null,
                answerRenderMarkdown = deepState.result.isNotBlank(),
                answerFooter = {
                    SmartDeepSeekAnswerFooter(
                        isLoading = deepState.isLoading,
                        modelLoaded = deepState.modelLoaded,
                        thinking = deepState.thinking,
                        generationMetrics = deepState.generationMetrics,
                        stageMetrics = deepState.stageMetrics
                    )
                },
                metaProvider = metaProvider,
                onOpenDetail = { id, blockIndex, blockId, detailHighlight ->
                    val encoded = Uri.encode(detailHighlight?.takeIf { it.isNotBlank() } ?: searchQuery)
                    navController.navigate(com.example.powerai.navigation.Screen.Detail.createRoute(id, encoded, blockIndex, blockId))
                },
                onRetry = { submitSmartQuery(searchQuery) },
                onCopy = onCopyToClipboard,
                showEmptyState = searchQuery.isNotBlank(),
                isPageLoading = deepState.isLoading,
                initialFirstVisibleItemIndex = smartScrollIndex,
                initialFirstVisibleItemScrollOffset = smartScrollOffset,
                onListPositionChange = hybridViewModel::setSmartScrollPosition,
                innerPadding = innerPadding
            )

            if (showSearchBar) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = panelMaxHeight)
                        .then(if (imeVisible) Modifier.imePadding() else Modifier.navigationBarsPadding())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        SearchBar(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            onSearch = { submitSmartQuery(searchQuery) },
                            onClear = onClear,
                            label = "搜索知识",
                            placeholder = "输入问题并调用本地模型",
                            autoFocus = showSearchBar
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (deepState.modelLoaded) "模型已加载" else "模型未加载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (deepState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            TextButton(
                                onClick = {
                                    if (deepState.modelLoaded) {
                                        deepSeekViewModel.onIntent(DeepSeekIntent.UnloadModel)
                                    } else {
                                        deepSeekViewModel.onIntent(DeepSeekIntent.LoadModel(SMART_DEEPSEEK_DEVICE_PATH, useGpu = deepState.backendMode.useGpu))
                                    }
                                }
                            ) { Text(if (deepState.modelLoaded) "卸载" else "加载") }
                            TextButton(onClick = { submitSmartQuery(searchQuery) }) { Text("搜索") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showAdvancedControls = !showAdvancedControls }) {
                                Text(if (showAdvancedControls) "收起高级设置" else "展开高级设置")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (searchQuery.isNotEmpty()) {
                                TextButton(onClick = onClear) { Text("清空") }
                            }
                            TextButton(onClick = { onShowSearchBarChange(false) }) { Text("关闭") }
                        }
                        if (showAdvancedControls) {
                            SmartDeepSeekAdvancedControls(
                                uiState = deepState,
                                searchQuery = searchQuery,
                                onIntent = deepSeekViewModel::onIntent,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "默认只显示搜索框，线程、后端与实验项已折叠，避免首屏被设置面板挡住。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
