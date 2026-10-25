package com.example.powerai.ui.screen.detail

// android.util.Log removed per TODO order; diagnostics suppressed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun produceFallbackChunksState(content: String): State<List<String>?> {
    return produceState<List<String>?>(
        initialValue = null,
        key1 = content
    ) {
        value = null
        // log removed: chunkContent START len=${content.length}
        val start = System.currentTimeMillis()
        value = withContext(Dispatchers.Default) {
            runCatching {
                chunkContent(content)
            }.getOrElse { t ->
                // log removed: chunkContent FAILED len=${content.length}
                listOf(content)
                listOf(content)
            }
        }
        // log removed: chunkContent DONE size=${value?.size ?: 0} took=${System.currentTimeMillis() - start}ms
    }
}
