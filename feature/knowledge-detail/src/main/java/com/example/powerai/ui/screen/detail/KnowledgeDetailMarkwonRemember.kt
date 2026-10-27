package com.example.powerai.ui.screen.detail

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.powerai.util.MarkwonHelper
import io.noties.markwon.Markwon

@Composable
internal fun rememberKnowledgeDetailMarkwon(context: Context): Markwon {
    // MarkwonHelper already caches instances; this mainly keeps Compose call-sites tidy.
    return remember(context) { MarkwonHelper.create(context) }
}
