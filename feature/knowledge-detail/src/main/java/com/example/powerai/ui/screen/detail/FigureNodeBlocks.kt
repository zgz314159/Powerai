package com.example.powerai.ui.screen.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.ui.blocks.ImageBlockItem
import com.example.powerai.ui.image.AssetImageUriNormalizer

@Composable
internal fun FigureNodeBlockItem(block: FigureNodeBlock, subfolder: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!block.label.isNullOrBlank()) {
            Text(
                text = block.label!!,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        block.images.forEach { src ->
            val normalized = AssetImageUriNormalizer.normalize(src)
            ImageBlockItem(
                block = ImageBlock(
                    id = "fig-child-${src.hashCode()}",
                    src = normalized,
                    pageNumber = block.pageNumber
                ),
                subfolder = subfolder
            )
        }

        if (!block.caption.isNullOrBlank()) {
            Text(
                text = block.caption!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
