package com.example.powerai.ui.screen.detail

import android.text.Spanned
// android.util.Log removed per TODO; heavy debug traces removed
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MarkdownChunkTextView(
    markdown: String,
    fontScale: Float = 1f,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
    onAnchorClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = rememberKnowledgeDetailMarkwon(context)

    // Compute processed markdown (asset path rewrites) off the UI thread to avoid blocking composition.
    val processedMarkdownState by produceState(initialValue = markdown, key1 = markdown) {
        // log removed: convertAssetPathsToImages START len=${markdown.length}
        val start = System.currentTimeMillis()
        value = withContext(Dispatchers.Default) {
            runCatching {
                convertAssetPathsToImages(markdown, context)
            }.getOrElse { t ->
                // log removed: convertAssetPathsToImages FAILED
                markdown
                markdown
            }
        }
        // log removed: convertAssetPathsToImages DONE took=${System.currentTimeMillis() - start}ms
    }
    val processedMarkdown = processedMarkdownState

    // Parse/render markdown off the UI thread to avoid expensive work in AndroidView.update.
    val renderedState by produceState<Spanned?>(initialValue = null, key1 = processedMarkdown) {
        value = null
        if (processedMarkdown.length > 20000) return@produceState
        // log removed: markwon parse/render START len=${processedMarkdown.length}
        val start = System.currentTimeMillis()
        value = withContext(Dispatchers.Default) {
            runCatching {
                val node = markwon.parse(processedMarkdown)
                markwon.render(node) as? Spanned
            }.getOrElse { t ->
                // log removed: markwon parse/render FAILED
                null
                null
            }
        }
        // log removed: markwon parse/render DONE took=${System.currentTimeMillis() - start}ms
    }
    val rendered = renderedState

    AndroidView(
        modifier = modifier,
        factory = {
            TextView(context).apply {
                setTextIsSelectable(true)
                setPadding(0, 0, 0, 0)
            }
        },
        update = { tv ->
            try {
                // Avoid expensive Markwon parsing on the UI thread; use pre-rendered spans when available.
                if (processedMarkdown.length > 20000) {
                    tv.text = processedMarkdown
                } else if (rendered != null) {
                    tv.text = rendered
                } else {
                    tv.text = processedMarkdown
                }
            } catch (_: Throwable) {
                tv.text = processedMarkdown
            }

            tv.setOnTouchListener { _, ev ->
                try {
                    val handled = KnowledgeDetailMarkwonInteractions.handleTapOrNull(tv, context, ev)
                    if (handled == true) return@setOnTouchListener true
                } catch (_: Throwable) {
                }
                return@setOnTouchListener false
            }
        }
    )
}
