package com.example.powerai.ui.screen.detail

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        viewModel.uiEffect.collect { eff ->
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
    val visionMarkdownByBlockId by viewModel.visionMarkdownByBlockId.collectAsState()
    val visionBoostingBlockId by viewModel.visionBoostingBlockId.collectAsState()
    val visionBoostErrorBlockId by viewModel.visionBoostErrorBlockId.collectAsState()
    val visionBoostErrorMessage by viewModel.visionBoostErrorMessage.collectAsState()
    val deepLogicValidationEnabled by viewModel.deepLogicValidationEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    message = "加载失败：${s.message}",
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is KnowledgeDetailUiState.Success -> {
                KnowledgeDetailSuccessContent(
                    navController = navController,
                    entity = s.entity,
                    rawHighlight = s.highlight,
                    initialBlockIndex = s.initialBlockIndex,
                    initialBlockId = s.initialBlockId,
                    deepLogicValidationEnabled = deepLogicValidationEnabled,
                    onToggleDeepLogicValidation = { enabled ->
                        viewModel.setDeepLogicValidationEnabled(enabled)
                    },
                    visionMarkdownByBlockId = visionMarkdownByBlockId,
                    visionBoostingBlockId = visionBoostingBlockId,
                    visionBoostErrorBlockId = visionBoostErrorBlockId,
                    visionBoostErrorMessage = visionBoostErrorMessage,
                    onRequestVisionBoost = { entityId, blockId, imageUri ->
                        viewModel.requestVisionBoost(entityId = entityId, blockId = blockId, imageUri = imageUri)
                    },
                    onApplyVisionBoostToOriginal = { entityId, rawBlockId, cacheKey, clearCacheAfter ->
                        viewModel.applyVisionBoostToOriginal(
                            entityId = entityId,
                            rawBlockId = rawBlockId,
                            cacheKey = cacheKey,
                            clearCacheAfter = clearCacheAfter
                        )
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
