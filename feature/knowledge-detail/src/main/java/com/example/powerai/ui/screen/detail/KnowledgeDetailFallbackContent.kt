package com.example.powerai.ui.screen.detail


import com.example.powerai.core.model.KnowledgeBlock
// android.util.Log removed per TODO; parsing diagnostics suppressed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.blocks.BlocksParser
import com.example.powerai.ui.blocks.KnowledgeBlocksColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun KnowledgeDetailFallbackContent(
    content: String,
    modifier: Modifier = Modifier
) {
    // If the content itself is a blocks JSON payload, parse on background dispatcher and render when ready.
    val parsedBlocksState by produceState<List<com.example.powerai.core.model.KnowledgeBlock>?>(initialValue = null, key1 = content) {
        val start = System.currentTimeMillis()
        // log removed: parseBlocks(produced) START contentLen=${content.length}
        value = withContext(Dispatchers.Default) {
            runCatching {
                BlocksParser.parseBlocks(content)
            }.getOrElse { t ->
                // log removed: parseBlocks(produced) FAILED contentLen=${content.length}
                null
            }
        }
        // log removed: parseBlocks(produced) DONE count=${value?.size ?: 0} took=${System.currentTimeMillis() - start}ms
    }

    val parsedBlocks = parsedBlocksState?.takeIf { it.isNotEmpty() }
    if (parsedBlocks != null) {
        // blocks-first rendering (no highlight in fallback path)
        KnowledgeBlocksColumn(blocks = parsedBlocks, highlight = "")
        return
    }

    val chunks by produceFallbackChunksState(content)

    if (chunks == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val list = chunks.orEmpty()
    // log removed: fallback render chunks size=${list.size}
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(list) { index, chunk ->
            KnowledgeDetailFallbackChunkItem(index = index, chunk = chunk)
        }
    }
}
