package com.example.powerai.ui.screen.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun produceBlocksComputedState(
    entityId: Long,
    contentBlocksJson: String?,
    highlight: String
): State<BlocksComputed?> {
    return produceState<BlocksComputed?>(
        initialValue = null,
        key1 = entityId,
        key2 = contentBlocksJson,
        key3 = highlight
    ) {
        val json = contentBlocksJson
        if (json.isNullOrBlank()) {
            value = BlocksComputed(emptyList(), emptyList(), emptyList())
            return@produceState
        }

        value = null
        value = withContext(Dispatchers.Default) {
            buildBlocksComputed(json, highlight)
        }
    }
}
