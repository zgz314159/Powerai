package com.example.powerai.ui.screen.detail

// android.util.Log removed per TODO; webview diagnostics removed
import android.webkit.WebView
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
internal fun MarkdownTableWebView(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val htmlState by produceState<String?>(initialValue = null, key1 = markdown) {
        // log removed: tableHtml START len=${markdown.length}
        val start = System.currentTimeMillis()
        value = withContext(Dispatchers.Default) {
            runCatching {
                buildHtmlForMarkdownTable(markdown)
            }.getOrElse { t ->
                // log removed: tableHtml FAILED len=${markdown.length}
                null
            }
        }
        // log removed: tableHtml DONE len=${value?.length ?: -1} took=${System.currentTimeMillis() - start}ms
    }
    val html = htmlState

    AndroidView(
        modifier = modifier,
        factory = {
            runCatching {
                WebView(context).apply {
                    KnowledgeDetailWebViewDefaults.applyTableDefaults(this)
                }
            }.getOrElse { t ->
                // log removed: WebView create FAILED; falling back to TextView
                TextView(context).apply {
                    text = markdown
                }
            }
        },
        update = { webView ->
            when (webView) {
                is WebView -> {
                    val htmlValue = html ?: return@AndroidView
                    try {
                        webView.loadDataWithBaseURL("file:///android_asset/", htmlValue, "text/html", "utf-8", null)
                    } catch (_: Throwable) {
                    }
                }

                is TextView -> {
                    webView.text = markdown
                }
            }
        }
    )
}
