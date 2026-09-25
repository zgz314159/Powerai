package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.feature.searchchat.SmartProgressUiState
import kotlinx.coroutines.delay

@Composable
internal fun SmartDeepSeekProgressCard(
    progressState: SmartProgressUiState,
    askedAtMillis: Long?,
    thinkingPreview: String,
    showAbort: Boolean,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elapsedMs by produceState(initialValue = 0L, key1 = askedAtMillis, key2 = progressState.phase) {
        while (askedAtMillis != null && progressState.phase != SmartProgressPhase.IDLE && progressState.showAsActive) {
            value = System.currentTimeMillis() - askedAtMillis
            delay(250)
        }
        value = if (askedAtMillis != null) {
            (System.currentTimeMillis() - askedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    fun headlineText(): String {
        return when (progressState.phase) {
            SmartProgressPhase.WAITING_FIRST_TOKEN -> {
                if (elapsedMs > 0L) {
                    "模型正在加载上下文并整理答案，已等待 ${String.format("%.1f", elapsedMs / 1000.0)}s"
                } else {
                    "模型正在加载上下文并整理答案"
                }
            }
            SmartProgressPhase.STOPPING -> "已发送停止请求，正在等待底层模型停稳"
            else -> buildString {
                append(progressState.statusText.ifBlank { "正在处理你的问题" })
                if (elapsedMs > 0L) {
                    append(" · 已耗时 ")
                    append(String.format("%.1fs", elapsedMs / 1000.0))
                }
            }
        }
    }

    fun detailText(): String {
        return when {
            progressState.phase == SmartProgressPhase.WAITING_FIRST_TOKEN -> {
                "首个可见答案出来前，底层仍会先做预填充和上下文整理。"
            }
            thinkingPreview.isNotBlank() && progressState.phase == SmartProgressPhase.THINKING -> {
                "已收到模型思考内容，继续等待最终答案。"
            }
            else -> progressState.detailText
        }
    }

    if (progressState.phase == SmartProgressPhase.IDLE && thinkingPreview.isBlank()) return

    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .heightIn(max = 240.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = progressState.phase.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = headlineText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
            )
            if (showAbort) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAbort) {
                    Text(text = "停止生成")
                }
            }
            if (detailText().isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = detailText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            }
            if (progressState.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "流程轨迹",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                progressState.steps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            if (thinkingPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "思考过",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = thinkingPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                )
            }
        }
    }
}
