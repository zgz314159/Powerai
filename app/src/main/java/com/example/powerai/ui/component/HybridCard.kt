package com.example.powerai.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeItem

@Composable
fun HybridCard(item: KnowledgeItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = item.title)
            Text(text = "来源: ${item.source}")
            Text(text = "分类: ${item.category}")
            Text(text = item.content.take(100) + if (item.content.length > 100) "..." else "")
        }
    }
}
