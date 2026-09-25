package com.example.powerai.ui.screen.main
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartThreadPreset

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.BuildConfig
import com.example.powerai.feature.searchchat.*

@Composable
internal fun SmartDeepSeekAdvancedControls(
    uiState: DeepSeekUiState,
    searchQuery: String,
    onIntent: (DeepSeekIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val selfBuiltJniEnabled = BuildConfig.DEEPSEEK_SELF_BUILT_JNI_ENABLED

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(4.dp))
        SmartDeepSeekControlRow(
            title = "回答模式",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = uiState.answerMode.label,
            options = SmartAnswerMode.entries.map { it.label to { onIntent(DeepSeekIntent.SetAnswerMode(it)) } }
        )
        Spacer(modifier = Modifier.height(6.dp))
        SmartDeepSeekControlRow(
            title = "后端",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = uiState.backendMode.label,
            options = SmartBackendMode.entries.map { it.label to { onIntent(DeepSeekIntent.SetBackendMode(it)) } }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (selfBuiltJniEnabled) {
                "自编 JNI 实验已开启。此开关需在 local.properties 设置 DEEPSEEK_SELF_BUILT_JNI_ENABLED=true 后重编。"
            } else {
                "当前为 AAR 路线。启用自编 JNI 请在 local.properties 设置 DEEPSEEK_SELF_BUILT_JNI_ENABLED=true 后重编。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        SmartDeepSeekControlRow(
            title = "线程",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = uiState.threadPreset.label,
            options = SmartThreadPreset.entries.map { it.label to { onIntent(DeepSeekIntent.SetThreadPreset(it)) } }
        )
        Spacer(modifier = Modifier.height(6.dp))
        SmartDeepSeekControlRow(
            title = "Prefill Batch",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = uiState.batchSizePreset.label,
            options = SmartBatchSizePreset.entries.map { it.label to { onIntent(DeepSeekIntent.SetBatchSizePreset(it)) } }
        )
        Spacer(modifier = Modifier.height(6.dp))
        SmartDeepSeekControlRow(
            title = "Prefill 线程",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = uiState.batchThreadPreset.label,
            options = SmartBatchThreadPreset.entries.map { it.label to { onIntent(DeepSeekIntent.SetBatchThreadPreset(it)) } }
        )
        Spacer(modifier = Modifier.height(6.dp))
        SmartDeepSeekControlRow(
            title = "前缀复用",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = if (uiState.prefixReuseEnabled) "已开" else "已关",
            options = listOf(
                "已开" to { onIntent(DeepSeekIntent.SetPrefixReuseEnabled(true)) },
                "已关" to { onIntent(DeepSeekIntent.SetPrefixReuseEnabled(false)) }
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        SmartDeepSeekControlRow(
            title = "状态快",
            modifier = Modifier.fillMaxWidth(),
            selectedLabel = if (uiState.stateSnapshotReuseEnabled) "实验开" else "实验关闭",
            options = listOf(
                "实验开" to { onIntent(DeepSeekIntent.SetStateSnapshotReuseEnabled(true)) },
                "实验关闭" to { onIntent(DeepSeekIntent.SetStateSnapshotReuseEnabled(false)) }
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = { onIntent(DeepSeekIntent.RunThreadLatencyBenchmark(searchQuery)) },
            enabled = uiState.modelLoaded && !uiState.isLoading && !uiState.threadBenchmarkState.isRunning && uiState.backendMode == SmartBackendMode.CPU && searchQuery.isNotBlank()
        ) {
            Text(if (uiState.threadBenchmarkState.isRunning) "线程基准运行.." else "4/6/8 线程基准")
        }
        SmartThreadBenchmarkSummary(
            state = uiState.threadBenchmarkState,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { onIntent(DeepSeekIntent.RunBatchSizeLatencyBenchmark(searchQuery)) },
            enabled = uiState.modelLoaded && !uiState.isLoading && !uiState.prefillBenchmarkState.isRunning && uiState.backendMode == SmartBackendMode.CPU && searchQuery.isNotBlank()
        ) {
            Text(if (uiState.prefillBenchmarkState.isRunning && uiState.prefillBenchmarkState.benchmarkTitle == "n_batch 基准") "n_batch 基准运行.." else "n_batch 基准")
        }
        TextButton(
            onClick = { onIntent(DeepSeekIntent.RunBatchThreadLatencyBenchmark(searchQuery)) },
            enabled = uiState.modelLoaded && !uiState.isLoading && !uiState.prefillBenchmarkState.isRunning && uiState.backendMode == SmartBackendMode.CPU && searchQuery.isNotBlank()
        ) {
            Text(if (uiState.prefillBenchmarkState.isRunning && uiState.prefillBenchmarkState.benchmarkTitle == "n_threads_batch 基准") "n_threads_batch 基准运行.." else "n_threads_batch 基准")
        }
        SmartPrefillBenchmarkSummary(
            state = uiState.prefillBenchmarkState,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { onIntent(DeepSeekIntent.RunStateSnapshotProbe) },
            enabled = uiState.modelLoaded && !uiState.isLoading && !uiState.stateSnapshotProbeRunning
        ) {
            Text(if (uiState.stateSnapshotProbeRunning) "状态快照探针运行中" else "跑状态快照探")
        }
        if (uiState.stateSnapshotStatus.isNotBlank()) {
            Text(
                text = uiState.stateSnapshotStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "线程/prefill 参数切换可即时下发；显式状态快照复用仍是实验路径。后端切换仍需重加模型。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
internal fun SmartDeepSeekControlRow(
    title: String,
    selectedLabel: String,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            options.forEachIndexed { index, (label, onClick) ->
                if (index > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
                FilterChip(
                    selected = selectedLabel == label,
                    onClick = onClick,
                    label = { Text(label) }
                )
            }
        }
    }
}
