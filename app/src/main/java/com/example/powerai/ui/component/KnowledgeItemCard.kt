package com.example.powerai.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import com.example.powerai.domain.model.KnowledgeItem

@Composable
fun KnowledgeItemCard(
    item: KnowledgeItem,
    highlight: String = "",
    expanded: Boolean = false,
    onExpand: (() -> Unit)? = null
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp, horizontal = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = item.title)
            Text(text = "分类: ${item.category}")
            Text(text = "来源: ${item.source}")
            if (expanded) {
                Text(text = getHighlightedPreview(item.content, highlight))
            } else {
                Text(text = getHighlightedPreview(item.content, highlight), modifier = Modifier
                    .background(Color.LightGray)
                    .padding(4.dp)
                    .then(if (onExpand != null) Modifier.clickable { onExpand() } else Modifier))
            }
        }
    }
}

private fun getHighlightedPreview(content: String, keyword: String): AnnotatedString {
    if (keyword.isBlank() || !content.contains(keyword, ignoreCase = true)) {
        return AnnotatedString(content.take(100) + if (content.length > 100) "..." else "")
    }
    val preview = content.take(100) + if (content.length > 100) "..." else ""
    val start = preview.indexOf(keyword, ignoreCase = true)
    return buildAnnotatedString {
        if (start >= 0) {
            append(preview.substring(0, start))
            withStyle(SpanStyle(background = Color.Yellow)) {
                append(preview.substring(start, start + keyword.length))
            }
            append(preview.substring(start + keyword.length))
        } else {
            append(preview)
        }
    }
}
