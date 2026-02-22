package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.screen.hybrid.HybridViewModel

@Composable
internal fun MainInlineDiagnostics(
    uiState: HybridViewModel.UiStateWithImport,
    onRetryImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    uiState.importProgress?.let { p ->
        val totalStr = p.totalItems?.let { "/$it" }.orEmpty()
        Text(
            text = "导入：${p.status} ${p.percent}% (${p.importedItems}$totalStr) ${p.fileName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp)
        )
        p.message?.takeIf { it.isNotBlank() }?.let { msg ->
            Text(
                text = msg.take(180),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }

    if (uiState.importProgress?.status == "failed") {
        TextButton(
            onClick = onRetryImport,
            modifier = modifier.padding(horizontal = 8.dp)
        ) {
            Text(text = "重试导入")
        }
    }
}
