@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiResponseSheet(
    aiUiState: com.example.powerai.ui.screen.hybrid.AiUiState,
    aiText: String,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (aiUiState) {
            is com.example.powerai.ui.screen.hybrid.AiUiState.Loading -> {
                Column { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(8.dp)); Text("AI 正在生成答案...", style = MaterialTheme.typography.bodySmall) }
            }
            is com.example.powerai.ui.screen.hybrid.AiUiState.Error -> {
                Text(text = aiUiState.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Row { Button(onClick = onRetry) { Text("重试") } }
            }
            is com.example.powerai.ui.screen.hybrid.AiUiState.Success, is com.example.powerai.ui.screen.hybrid.AiUiState.Idle -> {
                AiStreamCard(onRetry = onRetry)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = aiText)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onCopy(aiText) }) { Text("复制") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStreamCard(
    viewModel: com.example.powerai.ui.screen.main.AiStreamViewModel? = null,
    onRetry: () -> Unit = {}
) {
    val actualVm = viewModel ?: hiltViewModel<com.example.powerai.ui.screen.main.AiStreamViewModel>()
    val state by actualVm.aiStreamState.collectAsState()

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            when (state) {
                is com.example.powerai.ui.screen.main.AiStreamState.Idle -> Text("等待输入问题...")
                is com.example.powerai.ui.screen.main.AiStreamState.Loading -> {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("AI 正在生成答案...")
                    }
                }
                is com.example.powerai.ui.screen.main.AiStreamState.Success -> {
                    val text = (state as com.example.powerai.ui.screen.main.AiStreamState.Success).text
                    Text(text = text)
                }
                is com.example.powerai.ui.screen.main.AiStreamState.Error -> {
                    val msg = (state as com.example.powerai.ui.screen.main.AiStreamState.Error).message
                    Column {
                        Text(text = "AI 解析失败: $msg", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row {
                            Button(onClick = onRetry) { Text("重试") }
                            Spacer(modifier = Modifier.width(8.dp))
                            val ctx = LocalContext.current
                            Button(onClick = {
                                val androidCm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                androidCm.setPrimaryClip(android.content.ClipData.newPlainText("PowerAi", msg))
                            }) { Text("复制错误") }
                        }
                    }
                }
            }
        }
    }
}
