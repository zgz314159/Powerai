package com.example.powerai.ui.blocks









import com.example.powerai.core.model.UnknownBlock
import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.model.KnowledgeBlock
import android.content.Intent
import android.graphics.drawable.Drawable
// android.util.Log removed per TODO order; debug traces removed
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.powerai.ui.image.AssetImageUriNormalizer
import com.example.powerai.ui.screen.detail.PhotoViewerActivity
import com.example.powerai.ui.text.highlightAll
import com.example.powerai.core.model.util.BlocksJsonUtils

@Composable
fun KnowledgeBlocksColumn(
    blocks: List<KnowledgeBlock>,
    highlight: String,
    fontScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    // log removed: KnowledgeBlocksColumn render start count=${blocks.size}
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
    fontScale: Float = 1f
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

