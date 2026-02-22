package com.example.powerai.ui.screen.detail

import android.util.Log
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
        Log.d("PowerAi.Trace", "chunkContent START len=${content.length}")
        val start = System.currentTimeMillis()
        value = withContext(Dispatchers.Default) {
            runCatching {
                chunkContent(content)
            }.getOrElse { t ->
                Log.w(
                    "PowerAi.Trace",
                    "chunkContent FAILED len=${content.length} thread=${Thread.currentThread().name}",
                    t
                )
                listOf(content)
            }
        }
        Log.d("PowerAi.Trace", "chunkContent DONE size=${value?.size ?: 0} took=${System.currentTimeMillis() - start}ms")
    }
}
