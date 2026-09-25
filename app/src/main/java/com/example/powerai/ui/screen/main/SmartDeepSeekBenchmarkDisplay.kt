package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.feature.searchchat.SmartPrefillBenchmarkUiState
import com.example.powerai.feature.searchchat.SmartThreadBenchmarkUiState

@Composable
internal fun SmartPrefillBenchmarkSummary(
    state: SmartPrefillBenchmarkUiState,
    modifier: Modifier = Modifier
) {
    if (state.statusText.isBlank() && state.results.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        if (state.benchmarkTitle.isNotBlank()) {
            Text(
                text = state.benchmarkTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = state.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            state.results.forEach { result ->
                val detail = buildString {
                    append(result.dimensionLabel)
                    append(" · ")
                    append(
                        result.firstTokenLatencyMs?.let { "first token ${it}ms" }
                            ?: when (result.status) {
                                "completed" -> "first token -"
                                else -> result.status
                            }
                    )
                    result.answerStartLatencyMs?.let {
                        append(" · 首可见正文 ")
                        append(it)
                        append("ms")
                    }
                    result.errorMessage?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
internal fun SmartThreadBenchmarkSummary(
    state: SmartThreadBenchmarkUiState,
    modifier: Modifier = Modifier
) {
    if (state.statusText.isBlank() && state.results.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = state.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            state.results.forEach { result ->
                val detail = buildString {
                    append(result.threadLabel)
                    append(" · ")
                    append(
                        result.firstTokenLatencyMs?.let { "first token ${it}ms" }
                            ?: when (result.status) {
                                "completed" -> "first token -"
                                else -> result.status
                            }
                    )
                    result.answerStartLatencyMs?.let {
                        append(" · 首可见正文 ")
                        append(it)
                        append("ms")
                    }
                    result.errorMessage?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
