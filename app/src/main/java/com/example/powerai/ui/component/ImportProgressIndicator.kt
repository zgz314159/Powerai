package com.example.powerai.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.powerai.core.data.importer.ImportProgress

@Composable
@Suppress("DEPRECATION")
fun ImportProgressIndicator(
    progress: ImportProgress?,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f
) {
    if (progress == null) return

    val totalItems = progress.totalItems
    val percentFloat: Float? = when {
        progress.percent > 0 -> progress.percent.coerceIn(0, 100).toFloat()
        totalItems != null && totalItems > 0 ->
            (progress.importedItems.toFloat() / totalItems.toFloat() * 100f)
        else -> null
    }

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Importing ${progress.fileName} ${progress.importedItems}/${progress.totalItems?.toString().orEmpty().ifBlank { "?" }} (${percentFloat?.toInt() ?: 0}%)",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = MaterialTheme.typography.bodySmall.fontSize * fontScale,
                fontWeight = FontWeight.Normal
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (percentFloat != null) {
            LinearProgressIndicator(
                progress = (percentFloat / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
