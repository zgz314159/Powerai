@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.hybrid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.HybridCard
import com.example.powerai.ui.component.SearchBar

// ViewModel is supplied by the caller (avoid default composable viewModel())
@Composable
fun HybridScreen(viewModel: HybridViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SearchBar(
                value = input,
                onValueChange = { input = it },
                onSearch = { viewModel.submitQuery(input) },
                onClear = { input = "" }
            )
            Button(
                onClick = { viewModel.submitQuery(input) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("提交问题")
            }
            // Show import progress when available and between 0% and 100%
            uiState.importProgress?.let { progress ->
                val pct = progress.percent.coerceIn(0, 100)
                if (pct in 1..99 && progress.status == "in_progress") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "导入中: $pct%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            } else {
                Text(text = "AI答案：${uiState.answer}", modifier = Modifier.padding(top = 16.dp))
                LazyColumn(contentPadding = PaddingValues(top = 12.dp)) {
                    items(uiState.references) { item ->
                        HybridCard(item)
                    }
                }
            }
        }
    }
}
