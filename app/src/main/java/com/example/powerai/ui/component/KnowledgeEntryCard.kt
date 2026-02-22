package com.example.powerai.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeEntry
import com.example.powerai.util.PdfSourceRef

@Composable
fun KnowledgeEntryCard(
    entry: KnowledgeEntry,
    highlight: String? = null,
    fontScale: Float = 1.0f,
    modifier: Modifier = Modifier,
    onEdit: (KnowledgeEntry) -> Unit = {}
) {
    Card(
        modifier = modifier
            .clickable { onEdit(entry) }
            .padding(6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = buildAnnotatedString { appendHighlighted(entry.title, highlight) },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * fontScale
                )
            )

            Spacer(modifier = Modifier.size(6.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${entry.category.orEmpty()} • ${PdfSourceRef.display(entry.source)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                val statusColor = when (entry.status.lowercase()) {
                    "error", "garbled", "bad" -> Color(0xFFB00020)
                    "review" -> Color(0xFFFFA000)
                    "ok", "clean", "imported" -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.12f), shape = CircleShape)
                        .padding(6.dp)
                ) {
                    Text(entry.status.orEmpty(), color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendHighlighted(text: String?, query: String?) {
    if (text.isNullOrEmpty() || query.isNullOrEmpty()) {
        if (!text.isNullOrEmpty()) append(text)
        return
    }
    val lower = text.lowercase()
    val q = query.lowercase()
    var idx = 0
    var start = lower.indexOf(q, idx)
    while (start >= 0) {
        if (start > idx) append(text.substring(idx, start))
        val end = start + q.length
        withStyle(style = SpanStyle(color = Color(0xFFD32F2F))) {
            append(text.substring(start, end))
        }
        idx = end
        start = lower.indexOf(q, idx)
    }
    if (idx < text.length) append(text.substring(idx))
}
