package com.example.powerai.ui.screen.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.data.importer.PdfPromptInfo
import com.example.powerai.navigation.Screen

/**
 * 正式的用户知识库目录导入界面：用系统目录选择器选择 PaddleModels 输出目录，显示导入进度与
 * 明确的成功/失败/跳过结果，并在 KB 声明了 `pdf:{sha}::{name}` 时引导用户走既有 PDF 关联流程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("MagicNumber")
@Composable
fun KnowledgeImportScreen(
    navController: NavHostController,
    viewModel: ImportViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri != null) viewModel.onIntent(ImportIntent.ImportDirectory(uri))
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("导入知识库目录") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    "选择用 PaddleModels 生成、并已复制到手机上的知识库输出目录。" +
                        "该目录应直接包含 knowledge_base.json（以及它引用的 shots/ 图片）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { launcher.launch(null) },
                enabled = state !is KnowledgeImportUiState.Running,
            ) {
                Text("选择知识库目录")
            }
            ImportStatusContent(
                state = state,
                navController = navController,
                onReset = { viewModel.onIntent(ImportIntent.Reset) },
            )
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun ImportStatusContent(
    state: KnowledgeImportUiState,
    navController: NavHostController,
    onReset: () -> Unit,
) {
    when (state) {
        KnowledgeImportUiState.Idle ->
            Text("尚未导入。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        is KnowledgeImportUiState.Running -> {
            Text("正在导入：${state.displayName.ifBlank { "所选目录" }} · 已读取 ${state.importedItems} 条")
            CircularProgressIndicator()
        }

        is KnowledgeImportUiState.Success -> {
            Text("导入成功：${state.displayName}", color = Color(0xFF1B5E20))
            Text("条目 ${state.entries} · 内容块 ${state.blocks} · 截图 ${state.assets} 张")
            PdfAssociationPrompt(pdf = state.pdf, navController = navController)
        }

        is KnowledgeImportUiState.Skipped -> {
            Text("内容未变化，已跳过导入：${state.displayName}", color = Color(0xFF8A5A00))
            Text("现有条目 ${state.entries} 条保持不变。")
            PdfAssociationPrompt(pdf = state.pdf, navController = navController)
        }

        is KnowledgeImportUiState.Failed -> {
            Text("导入失败：${state.reason}", color = Color(0xFFB3261E))
            Text("已保留原有知识库，未产生半包。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onReset) { Text("重试") }
        }

        KnowledgeImportUiState.Cancelled -> {
            Text("导入已取消，可重新选择目录。", color = Color(0xFF8A5A00))
            OutlinedButton(onClick = onReset) { Text("重试") }
        }
    }
}

@Composable
private fun PdfAssociationPrompt(
    pdf: PdfPromptInfo?,
    navController: NavHostController,
) {
    if (pdf == null) return
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "此知识库来自 PDF《${pdf.fileName}》，尚未关联原 PDF。关联后可在搜索/详情中定位到原 PDF。",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(
        onClick = {
            navController.navigate(
                Screen.PdfViewer.createRoute(
                    fileId = pdf.fileId,
                    name = Uri.encode(pdf.fileName),
                    page = null,
                    bboxEncoded = null,
                ),
            )
        },
    ) {
        Text("选择原 PDF 关联")
    }
}
