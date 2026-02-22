package com.example.powerai.ui.jsonrepo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.powerai.data.json.JsonKnowledgeFile

@Composable
fun FileRow(file: JsonKnowledgeFile, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Entries: ${file.entriesCount}", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(file.importTimestamp.toString(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
