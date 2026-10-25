package com.example.powerai.ui.screen.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.util.PdfAssociation
import com.example.powerai.util.PdfAssociationResult
import com.example.powerai.util.PdfOutlineIndexStore
import com.example.powerai.util.PdfStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    navController: NavHostController,
    fileId: String,
    fileName: String,
    initialHighlightPage: Int? = null,
    initialHighlightBboxJson: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    val pdfFile = remember(fileId, refreshKey) { PdfStorage.getPdfFile(context, fileId) }
    var attemptedAssetRestore by remember(fileId) { mutableStateOf(false) }
    var isRestoringFromAssets by remember(fileId) { mutableStateOf(false) }

    var missingHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileId) {
        // Best-effort: for newly-installed app, original PDFs may still exist in assets.
        // Restore them automatically (no runtime PDF parsing; uses prebuilt index file).
        if (!attemptedAssetRestore) {
            attemptedAssetRestore = true
            if (!pdfFile.exists()) {
                isRestoringFromAssets = true
                val restored =
                    withContext(Dispatchers.IO) {
                        PdfOutlineIndexStore.restorePdfFromAssetsIfPossible(context, fileId)
                    }
                isRestoringFromAssets = false
                if (restored) {
                    missingHint = "已从 assets 自动恢复原 PDF 并刷新。"
                    refreshKey++
                }
            }
        }
    }
    val relinkLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                missingHint = null
                if (uri == null) return@rememberLauncherForActivityResult
                // 校验所选 PDF 的 sha256 与 fileId 一致后再关联；文件较大，放到 IO 线程。
                scope.launch(Dispatchers.IO) {
                    val result = PdfAssociation.associate(context, uri, fileId)
                    withContext(Dispatchers.Main) {
                        when (result) {
                            is PdfAssociationResult.Associated -> {
                                missingHint = "已绑定原 PDF 并刷新。"
                                refreshKey++
                            }
                            is PdfAssociationResult.Mismatch ->
                                missingHint = "所选 PDF 与目录不匹配，请选择原始 PDF。期望 fileId=$fileId，实际=${result.actual}"
                            is PdfAssociationResult.Failed ->
                                missingHint = "关联失败：${result.reason}"
                        }
                    }
                }
            },
        )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = if (fileName.isBlank()) "PDF" else fileName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        if (!pdfFile.exists()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isRestoringFromAssets) "正在从 assets 恢复原 PDF" else "找不到 PDF 原件（该文件未经此应用恢复）")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "fileId=$fileId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { relinkLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("选择 PDF 重新关联")
                    }
                    missingHint?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            return@Scaffold
        }

        PdfPagesColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            pdfFile = pdfFile,
            highlightPageIndex = initialHighlightPage?.minus(1),
            highlightBox =
                remember(initialHighlightBboxJson) {
                    parsePdfBoundingBoxOrNull(initialHighlightBboxJson)
                },
        )
    }
}
