package com.example.powerai.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.powerai.data.local.entity.KnowledgeEntity

@Composable
fun DebugScreen(viewModel: com.example.powerai.ui.debug.DebugViewModel) {
    val items by viewModel.items.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { viewModel.reloadAll() }) { Text("Reload") }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { e ->
                EditableEntry(e = e, onSave = { updated -> viewModel.updateEntry(updated) })
            }
        }
    }
}

@Composable
fun EditableEntry(e: KnowledgeEntity, onSave: (KnowledgeEntity) -> Unit) {
    var text by remember { mutableStateOf(e.content) }
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Content") })
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Button(onClick = { onSave(e.copy(content = text)) }) { Text("Save") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "id:${e.id} source:${e.source}")
        }
    }
}
