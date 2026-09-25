package com.example.powerai.feature.searchchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DeepSeekScreen(viewModel: DeepSeekViewModel = hiltViewModel(), defaultDevicePath: String? = null) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState.isLoading
    val modelLoaded = uiState.modelLoaded
    val thinking = uiState.thinking
    val result = uiState.result
    val generationMetrics = uiState.generationMetrics

    var resolvedDefault by remember {
        mutableStateOf(defaultDevicePath ?: "")
    }
    // prefer app files/models/<name> (imported via Gradle task), else fallback to external provided path
    LaunchedEffect(Unit) {
        resolvedDefault = "/storage/emulated/0/Android/data/com.example.powerai/files"
    }

    var devicePath by remember { mutableStateOf(defaultDevicePath ?: "") }
    var prompt by remember { mutableStateOf("") }
    var maxTokensInput by remember { mutableStateOf("64") }

    LaunchedEffect(resolvedDefault) {
        if (devicePath.isBlank() && resolvedDefault.isNotBlank()) {
            devicePath = resolvedDefault
        }
    }

    Column(modifier = Modifier.padding(12.dp)) {
        OutlinedTextField(
            value = devicePath,
            onValueChange = { devicePath = it },
            label = { Text("模型设备路径") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { viewModel.loadDeepSeekFromDevicePath(devicePath, useGpu = false) }) {
                Text(text = "加载模型")
            }
            Button(onClick = { viewModel.unloadDeepSeek() }, modifier = Modifier.padding(start = 8.dp)) {
                Text(text = "卸载模型")
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }

        Text(text = "已加载： ${if (modelLoaded) "是" else "否"}", style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("用户提示 (Prompt)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "当前本地模型上下文较小，建议问题尽量控制在一句话内。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        OutlinedTextField(
            value = maxTokensInput,
            onValueChange = { maxTokensInput = it.filter(Char::isDigit).take(4) },
            label = { Text("最大输出 tokens") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = {
                val maxTokens = maxTokensInput.toIntOrNull()?.coerceIn(24, 96) ?: 64
                viewModel.generate(prompt, maxTokens)
            }) {
                Text(text = "生成")
            }
        }

        Text(
            text = buildString {
                append("首 token 延迟: ")
                append(generationMetrics.firstTokenLatencyMs?.let { "${it}ms" } ?: "-")
                append("  |  已收片段: ")
                append(generationMetrics.streamedTokenCount)
                append("  |  片段/s: ")
                append(generationMetrics.tokensPerSecond?.let { String.format("%.2f", it) } ?: "-")
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Text(text = "结果:", style = MaterialTheme.typography.titleMedium)
        Text(text = result, modifier = Modifier.padding(6.dp))

        // Thinking panel: toggleable (折叠面板)，并展示流式捕获内容<think>
        var thinkingCollapsed by remember { mutableStateOf(true) }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { thinkingCollapsed = !thinkingCollapsed }) {
                Text(text = if (thinkingCollapsed) "展开 思维链" else "折叠 思维链")
            }
        }

        if (!thinkingCollapsed) {
            if (thinking.isEmpty()) {
                Text(text = "暂无思维链", modifier = Modifier.padding(6.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(thinking) { seg ->
                        Text(text = seg, modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp))
                    }
                }
            }
        }
    }
}
