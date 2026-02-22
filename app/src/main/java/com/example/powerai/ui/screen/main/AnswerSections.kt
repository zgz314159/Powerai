@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeItem

/**
 * Small, reusable Compose components for the three answer display areas.
 * Each file keeps a single responsibility and remains short.
 */

@Composable
fun AiAnswerArea(answer: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "AI 搜索答案", modifier = Modifier.padding(bottom = 6.dp))
            Text(text = if (answer.isBlank()) "无 AI 结果" else answer)
        }
    }
}

@Composable
fun LocalAnswerArea(items: List<KnowledgeItem>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "本地数据答案", modifier = Modifier.padding(bottom = 6.dp))
            if (items.isEmpty()) {
                Text(text = "未找到本地结果")
            } else {
                items.forEach { it ->
                    Text(text = "- ${it.title}: ${it.content}", modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
fun SmartAnswerArea(aiAnswer: String, localItems: List<KnowledgeItem>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "智能综合答案", modifier = Modifier.padding(bottom = 6.dp))
            if (localItems.isNotEmpty()) {
                Text(text = "本地参考：")
                localItems.forEach { it ->
                    Text(text = "- ${it.title}: ${it.content}", modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(text = "AI 总结：")
            Text(text = if (aiAnswer.isBlank()) "无 AI 结果" else aiAnswer)
        }
    }
}
