package com.example.powerai.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.ui.settings.SettingsViewModel

@Composable
fun SettingsScreen(navController: NavHostController, viewModel: SettingsViewModel) {
    val progress by viewModel.importProgress.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.importUri(it) } }
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { launcher.launch(arrayOf("text/plain", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")) }) { Text("导入文件") }
            Button(onClick = { navController.navigate("jsonrepo") }) { Text("JSON资源库") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "字体大小: ${String.format("%.2f", fontSize)}")
        Slider(value = fontSize, onValueChange = { viewModel.setFontSize(it) }, valueRange = 0.75f..2.0f, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))

        Spacer(modifier = Modifier.height(12.dp))

        progress?.let { p ->
            Text(text = "导入 ${p.fileName}: ${p.status} ${p.percent}% (${p.importedItems})")
        }
    }
}
