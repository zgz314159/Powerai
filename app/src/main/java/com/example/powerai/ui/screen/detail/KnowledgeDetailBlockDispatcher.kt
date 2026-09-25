package com.example.powerai.ui.screen.detail









import com.example.powerai.core.model.UnknownBlock
import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.model.KnowledgeBlock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.blocks.*
import com.example.powerai.ui.image.AssetImageUriNormalizer

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KnowledgeDetailReadingBlockItem(
    block: KnowledgeBlock,
    highlight: String,
    fontScale: Float,
    subfolder: String? = null,
    onAnchorClick: ((String) -> Unit)? = null
) {
    var showPeek by remember { mutableStateOf(false) }
    val peekUri = remember(block.imageUri, subfolder) {
        block.imageUri?.let { AssetImageUriNormalizer.normalize(it) }
    }

    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { if (!peekUri.isNullOrBlank()) showPeek = true }
            )
    ) {
        when (block) {
            is TextBlock -> DetailTextBlockItem(block = block, highlight = highlight, fontScale = fontScale, onAnchorClick = onAnchorClick)
            is ImageBlock -> ImageBlockItem(block = block, subfolder = subfolder)
            is FigureNodeBlock -> FigureNodeBlockItem(block = block, subfolder = subfolder)
            is ListBlock -> DetailListBlockItem(block = block, highlight = highlight, fontScale = fontScale)
            is TableBlock -> DetailTableBlockItem(block = block, highlight = highlight, fontScale = fontScale, subfolder = subfolder)
            is CodeBlock -> DetailCodeBlockItem(block = block, fontScale = fontScale)
            is UnknownBlock -> DetailUnknownBlockItem(block = block, highlight = highlight, fontScale = fontScale)
        }
    }

    if (showPeek && !peekUri.isNullOrBlank()) {
        VisualPeekPopup(
            imageUri = peekUri,
            caption = (block as? TextBlock)?.text?.take(30) ?: "原文查验",
            onDismiss = { showPeek = false }
        )
    }
}
