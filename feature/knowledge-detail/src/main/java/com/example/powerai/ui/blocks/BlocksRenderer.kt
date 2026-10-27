package com.example.powerai.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.model.UnknownBlock

// android.util.Log removed per TODO order; debug traces removed

@Composable
fun KnowledgeBlocksColumn(
    blocks: List<KnowledgeBlock>,
    highlight: String,
    fontScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    // log removed: KnowledgeBlocksColumn render start count=${blocks.size}
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(blocks) { _, block ->
            KnowledgeBlockItem(block = block, highlight = highlight, fontScale = fontScale)
        }
    }
    // log removed: KnowledgeBlocksColumn render end
}

@Composable
fun KnowledgeBlockItem(
    block: KnowledgeBlock,
    highlight: String,
    fontScale: Float = 1f,
) {
    when (block) {
        is TextBlock -> TextBlockItem(block, highlight)
        is ImageBlock -> ImageBlockItem(block)
        is ListBlock -> ListBlockItem(block, highlight)
        is TableBlock -> TableBlockItem(block, highlight)
        is CodeBlock -> CodeBlockItem(block)
        is FigureNodeBlock -> FigureNodeBlockItem(block)
        is UnknownBlock -> UnknownBlockItem(block, highlight)
    }
}

@Composable
fun FigureNodeBlockItem(block: FigureNodeBlock) {
    Column(modifier = Modifier.fillMaxWidth()) {
        block.images.forEach { uri ->
            ImageBlockItem(ImageBlock(id = block.id, src = uri, caption = block.caption, pageNumber = block.pageNumber))
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
