@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: SettingsViewModel) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.onIntent(SettingsIntent.ImportUri(it)) } }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("设置") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { launcher.launch(arrayOf("text/plain", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "application/json", "*/*")) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("导入文件")
            }
        }
    ) { inner ->
        SettingsBody(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(inner)
        )
    }
}

@Composable
fun SettingsBody(
    navController: NavHostController,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    topContent: (@Composable () -> Unit)? = null
) {
    val progress by viewModel.importProgress.collectAsState()
    val detailContentFontScale by viewModel.detailContentFontScale.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val pdfDiag = uiState.pdfDiagnostics

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        topContent?.invoke()

        SettingsSection(title = "资源") {
            SettingsActionRow(label = "JSON 资源", actionLabel = "打开") { navController.navigate("jsonrepo") }
        }

        Spacer(Modifier.height(12.dp))

        SettingsSection(title = "外观") {
            Text(text = "词条详情正文字号: ${String.format("%.2f", detailContentFontScale)}")
            Slider(
                value = detailContentFontScale,
                onValueChange = { viewModel.onIntent(SettingsIntent.SetDetailContentFontScale(it)) },
                valueRange = 0.75f..3.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Version display + hidden dev backdoor
        val versionName = uiState.versionName
        val context = LocalContext.current

        // observe one-shot backdoor event and launch activity when it fires
        LaunchedEffect(viewModel) {
            viewModel.devBackdoor.collect {
                try {
                    val intent = Intent(context, com.example.powerai.ui.test.EmbeddingTestActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Throwable) {}
            }
        }

        Text(
            text = "版本 $versionName",
            modifier = Modifier
                .clickable { viewModel.onIntent(SettingsIntent.OnVersionTapped) }
                .padding(vertical = 6.dp)
        )

        progress?.let { p ->
            Text(text = "导入 ${p.fileName}: ${p.status} ${p.percent}% (${p.importedItems})")
            if (!p.message.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = p.message.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            if (p.fileId.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = "fileId: ${p.fileId}", style = MaterialTheme.typography.bodySmall)
            }

            pdfDiag?.let { d ->
                if (d.fileId == p.fileId && d.fileName == p.fileName && p.status == "imported") {
                    Spacer(Modifier.height(10.dp))
                    Text(text = "PDF 入库检查：写入记录${d.rowsInDb}", style = MaterialTheme.typography.bodySmall)
                    val hit1 = d.keywordHitCounts["回路"]
                    val hit2 = d.keywordHitCounts["电缆"]
                    Text(text = "命中：回$hit1，电缆槽=$hit2", style = MaterialTheme.typography.bodySmall)
                    if (d.samples.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = "抽取示例${d.samples.joinToString(" | ")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
