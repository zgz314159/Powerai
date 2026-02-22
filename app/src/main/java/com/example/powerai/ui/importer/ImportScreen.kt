package com.example.powerai.ui.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ImportScreen(
    onPickFiles: (List<Uri>) -> Unit = {},
    viewModel: ImportViewModel
) {
    val progress by viewModel.progress.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { onPickFiles(listOf(it)) }
        }
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
            Text("Pick file (SAF)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        progress?.let { p ->
            Text(text = "${p.fileName}: ${p.status} ${p.percent}%")
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(progress = p.percent / 100f)
        }
    }
}
