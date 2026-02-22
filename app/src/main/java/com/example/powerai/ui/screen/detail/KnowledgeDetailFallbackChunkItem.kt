package com.example.powerai.ui.screen.detail

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun KnowledgeDetailFallbackChunkItem(
    index: Int,
    chunk: String,
    modifier: Modifier = Modifier
) {
    val isTable = runCatching { looksLikeMarkdownTableChunk(chunk) }.getOrDefault(false)

    Log.d(
        "PowerAi.Trace",
        "fallback chunk render index=$index len=${chunk.length} isTable=$isTable thread=${Thread.currentThread().name}"
    )

    if (isTable) {
        // Avoid WebView and Markwon table rendering here: both can be extremely heavy and trigger ANR/GC churn.
        MarkdownTableCompose(
            markdown = chunk,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
        )
    } else {
        MarkdownChunkTextView(
            markdown = chunk,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )
    }
}
