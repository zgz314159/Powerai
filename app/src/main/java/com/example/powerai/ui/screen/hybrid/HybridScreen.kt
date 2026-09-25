@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
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
    val aiAnswer = uiState.aiAnswer
    val evidence = uiState.evidenceList
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
            // display what the backend actually sees after sanitization
            Text(
                text = "当前检索词${uiState.sanitizedQuestion}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
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
                        Text(text = "导入 $pct%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            } else {
                // AI summary area (top) with small robot icon
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "🤖", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(text = aiAnswer ?: "(暂无 AI 摘要)")
                }

                // Evidence gallery (horizontally scrollable) below the AI answer
                if (evidence.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(top = 12.dp)) {
                        items(
                            items = evidence,
                            key = { rr -> rr.id ?: rr.item?.id ?: rr.hashCode().toLong() }
                        ) { rr ->
                            EvidenceCard(rr)
                        }
                    }
                } else {
                    // Fallback to old references list if available
                    LazyColumn(contentPadding = PaddingValues(top = 12.dp)) {
                        itemsIndexed(
                            items = uiState.references,
                            key = { index, item -> "${item.id}_$index" }
                        ) { index: Int, item: com.example.powerai.core.model.KnowledgeItem ->
                            HybridCard(item)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EvidenceCard(rr: com.example.powerai.core.model.RetrievalResult) {
    val title = rr.item?.title ?: rr.metadata["title"] ?: "(无标"
    val src = rr.item?.source ?: rr.source.ifBlank { "local" }
    val snippet = rr.item?.content?.take(160) ?: rr.metadata["snippet"] ?: "(无摘"

    androidx.compose.material3.Card(
        modifier = Modifier
            .width(260.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "来源: $src", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = snippet, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
