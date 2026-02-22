package com.example.powerai.ui.jsonrepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.data.json.JsonEntry

@Composable
fun EntryRow(entry: JsonEntry, onSave: (JsonEntry) -> Unit) {
    var title by remember { mutableStateOf(entry.title.orEmpty()) }
    var content by remember { mutableStateOf(entry.content.orEmpty()) }

    val statusColor = when (entry.status) {
        "parsed" -> MaterialTheme.colorScheme.primary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onBackground
    }

    ElevatedCard(Modifier.fillMaxWidth().padding(6.dp)) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, maxLines = 6)
            Spacer(Modifier.height(8.dp))
            Text(text = "状态: ${entry.status}", color = statusColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onSave(entry.copy(title = title, content = content)) }) { Text("Save") }
            }
        }
    }
}
