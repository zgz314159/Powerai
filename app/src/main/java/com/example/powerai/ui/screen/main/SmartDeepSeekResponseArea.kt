package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.SmartStageMetrics

@Composable
internal fun SmartDeepSeekAnswerFooter(
    isLoading: Boolean,
    modelLoaded: Boolean,
    thinking: List<String>,
    generationMetrics: GenerationMetrics,
    stageMetrics: SmartStageMetrics
) {
    if (!modelLoaded && !isLoading && generationMetrics.streamedTokenCount == 0 && thinking.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = buildString {
                append(stageMetrics.modeLabel)
                append(" · ")
                append(stageMetrics.backendLabel)
                append(" · ")
                append(stageMetrics.threadLabel)
                append('\n')
                append("检 ")
                append(stageMetrics.retrievalMs?.let { "${it}ms" } ?: "-")
                append("  |  Prompt: ")
                append(stageMetrics.promptBuildMs?.let { "${it}ms" } ?: "-")
                append("  |  证据: ")
                append(stageMetrics.evidenceCount)
                append("  |  Prompt字符: ")
                append(stageMetrics.promptChars)
                append('\n')
                append("token 延迟: ")
                append(generationMetrics.firstTokenLatencyMs?.let { "${it}ms" } ?: "-")
                append("  |  已收片段: ")
                append(generationMetrics.streamedTokenCount)
                append("  |  片段/s: ")
                append(generationMetrics.tokensPerSecond?.let { String.format("%.2f", it) } ?: "-")
                append("  |  正文 字符/s: ")
                append(stageMetrics.decodeCharsPerSecond?.let { String.format("%.2f", it) } ?: "-")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
