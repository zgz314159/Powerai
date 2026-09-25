package com.example.powerai.ui.screen.detail

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDetailScreen(
    navController: NavHostController,
    viewModel: KnowledgeDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { eff ->
            when (eff) {
                is KnowledgeDetailUiEffect.Toast -> {
                    try {
                        Toast.makeText(context, eff.message, Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val successState = uiState as? KnowledgeDetailUiState.Success
    val visionMarkdownByBlockId = successState?.visionMarkdownByBlockId ?: emptyMap()
    val visionBoostingBlockId = successState?.visionBoostingBlockId
    val visionBoostErrorBlockId = successState?.visionBoostErrorBlockId
    val visionBoostErrorMessage = successState?.visionBoostErrorMessage
    val deepLogicValidationEnabled = successState?.deepLogicValidationEnabled ?: false
    val fontScale by viewModel.detailContentFontScale.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("词条详情设置") },
            text = {
                DetailFontScaleSection(
                    fontScale = fontScale,
                    onFontScaleChange = { value -> viewModel.onIntent(DetailIntent.SetDetailContentFontScale(value)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) {
                    Text("完成")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多设置")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("调节正文字号") },
                            onClick = {
                                showMenu = false
                                showFontDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val s = uiState) {
            is KnowledgeDetailUiState.Loading -> {
                KnowledgeDetailCenteredLoading(
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is KnowledgeDetailUiState.NotFound -> {
                KnowledgeDetailCenteredMessage(
                    message = "未找到该条目",
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is KnowledgeDetailUiState.Error -> {
                KnowledgeDetailCenteredMessage(
                    message = "加载失败${s.message}",
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is KnowledgeDetailUiState.Success -> {
                KnowledgeDetailSuccessContent(
                    navController = navController,
                    entity = s.entity,
                    sourceFileName = s.sourceFileName,
                    rawHighlight = s.highlight,
                    entryNavigation = s.entryNavigation,
                    initialBlockIndex = s.initialBlockIndex,
                    initialBlockId = s.initialBlockId,
                    fontScale = fontScale,
                    enableSwipeNavigation = !showFontDialog,
                    deepLogicValidationEnabled = deepLogicValidationEnabled,
                    onToggleDeepLogicValidation = { enabled ->
                        viewModel.onIntent(DetailIntent.SetDeepLogicValidation(enabled))
                    },
                    visionMarkdownByBlockId = visionMarkdownByBlockId,
                    visionBoostingBlockId = visionBoostingBlockId,
                    visionBoostErrorBlockId = visionBoostErrorBlockId,
                    visionBoostErrorMessage = visionBoostErrorMessage,
                    onRequestVisionBoost = { entityId, blockId, imageUri ->
                        viewModel.onIntent(DetailIntent.RequestVisionBoost(entityId = entityId, blockId = blockId, imageUri = imageUri))
                    },
                    onApplyVisionBoostToOriginal = { entityId, rawBlockId, cacheKey, clearCacheAfter ->
                        viewModel.onIntent(DetailIntent.ApplyVisionBoost(
                            entityId = entityId,
                            rawBlockId = rawBlockId,
                            cacheKey = cacheKey,
                            clearCacheAfter = clearCacheAfter
                        ))
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
