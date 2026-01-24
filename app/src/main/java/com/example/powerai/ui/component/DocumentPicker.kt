package com.example.powerai.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Compose 组件：文件选择（SAF，支持 PDF/TXT），通过回调返回 Uri。
 */
@Composable
fun DocumentPicker(onDocumentPicked: (Uri?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> onDocumentPicked(uri) }
    )
    val mimeTypes = remember { arrayOf("application/pdf", "text/plain") }
    Button(onClick = { launcher.launch(mimeTypes) }) {
        Text("选择文件")
    }
}
