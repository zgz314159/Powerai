package com.example.powerai.ui.component

import com.example.powerai.core.model.KnowledgeItem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.theme.HighlightYellow

@Composable
fun KnowledgeItemCard(
    item: KnowledgeItem,
    highlight: String = "",
    highlightStyleOverride: SpanStyle? = null,
    metaLine: String? = null,
    isSelected: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    previewMaxLines: Int = 4,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    OutlinedCard(
        modifier = clickableModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val highlightStyle = highlightStyleOverride ?: SpanStyle(
                background = HighlightYellow,
                color = Color.Unspecified
            )

            val titlePreview = remember(
                item.title,
                highlight,
                highlightStyle.background,
                highlightStyle.color
            ) {
                // Title can be very long (some KBs put the whole paragraph into title/jobTitle).
                // Use the same snippet-around-hit strategy so the keyword is visible even if it appears late.
                buildHighlightedPreview(
                    fullText = item.title,
                    keyword = highlight,
                    highlightStyle = highlightStyle,
                    maxChars = 140,
                    beforeChars = 8
                )
            }

            Text(
                text = titlePreview,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val meta = (metaLine ?: item.contextLabel)?.takeIf { it.isNotBlank() }
            if (meta != null) {
                Spacer(modifier = Modifier.padding(top = 2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.padding(top = 6.dp))
            Row {
                Text(
                    text = "证据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))

                val contentForPreview = remember(item.content) {
                    // Collapse newlines/whitespace for stable line wrapping.
                    // Without this, the hit may exist but be pushed beyond maxLines by hard line breaks.
                    item.content.replace(Regex("\\s+"), " ").trim()
                }
                val preview = remember(
                    contentForPreview,
                    highlight,
                    highlightStyle.background,
                    highlightStyle.color
                ) {
                    buildHighlightedPreview(
                        fullText = contentForPreview,
                        keyword = highlight,
                        highlightStyle = highlightStyle,
                        maxChars = 220,
                        beforeChars = 0
                    )
                }
                if (expanded) {
                    Text(text = preview)
                } else {
                    Text(
                        text = preview,
                        maxLines = previewMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onExpand != null) Modifier.clickable { onExpand() } else Modifier
                    )
                }
            }
        }
    }
}
