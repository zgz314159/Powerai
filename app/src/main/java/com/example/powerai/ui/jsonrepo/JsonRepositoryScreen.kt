package com.example.powerai.ui.jsonrepo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.data.json.JsonEntry
import com.example.powerai.data.json.JsonKnowledgeFile
import java.io.File

@Composable
fun JsonRepositoryScreen(navController: NavHostController, viewModel: JsonRepositoryViewModel) {
    val files by viewModel.files.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val fileStats by viewModel.fileStats.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedFileId by remember { mutableStateOf<String?>(null) }
    var showEntries by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("JSON Library", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Files", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { viewModel.loadFiles() }) { Text("Refresh") }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.height(160.dp)) {
            items(files) { f: JsonKnowledgeFile ->
                FileRow(f) {
                    selectedFileId = f.fileId
                    viewModel.selectFile(f.fileId)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Entries", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = filter, onValueChange = { filter = it }, label = { Text("Search entries") }, singleLine = true, modifier = Modifier.fillMaxWidth(0.6f))
        }
        Spacer(Modifier.height(8.dp))

        if (!showEntries) {
            Text("Select a file to view its entries.")
        } else {
            val displayed = if (filter.isBlank()) entries else entries.filter { it.title.contains(filter, true) || it.content.contains(filter, true) }
            if (displayed.isEmpty()) {
                Text("No matching entries")
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(displayed) { e: JsonEntry ->
                        EntryRow(e, onSave = { updated ->
                            val fileId = selectedFileId ?: return@EntryRow
                            viewModel.updateEntry(fileId, updated)
                        })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        importProgress?.let { p ->
            val progressFloat = if (p.totalItems != null && p.totalItems > 0) {
                p.importedItems.toFloat() / p.totalItems.toFloat()
            } else {
                (p.percent.toFloat() / 100f).coerceIn(0f, 1f)
            }
            LinearProgressIndicator(progress = progressFloat, Modifier.fillMaxWidth())
            Text("Import: ${p.status} ${p.importedItems}/${p.totalItems ?: "-"}")
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                val fid = selectedFileId ?: run {
                    coroutineScope.launch { snackbarHostState.showSnackbar("请选择一个文件以导出") }
                    return@Button
                }
                val target = File(context.filesDir, "export_${fid}.csv")
                viewModel.exportCsv(fid, target) { ok ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(if (ok) "CSV 导出成功: ${target.name}" else "CSV 导出失败") }
                }
            }) { Text("Export CSV") }

            Spacer(Modifier.width(8.dp))

            Button(onClick = {
                val fid = selectedFileId ?: run {
                    coroutineScope.launch { snackbarHostState.showSnackbar("请选择一个文件以导出") }
                    return@Button
                }
                val target = File(context.filesDir, "export_${fid}.json")
                viewModel.exportJson(fid, target) { ok ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(if (ok) "JSON 导出成功: ${target.name}" else "JSON 导出失败") }
                }
            }) { Text("Export JSON") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

}


@Composable
private fun FileRow(file: JsonKnowledgeFile, onSelect: () -> Unit) {
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

@Composable
private fun EntryRow(entry: JsonEntry, onSave: (JsonEntry) -> Unit) {
    var title by remember { mutableStateOf(entry.title ?: "") }
    var content by remember { mutableStateOf(entry.content ?: "") }

    val statusColor = when (entry.status) {
        "parsed" -> MaterialTheme.colorScheme.primary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onBackground
    }

    Card(Modifier.fillMaxWidth().padding(6.dp)) {
        Column(Modifier.padding(8.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, maxLines = 6)
            Spacer(Modifier.height(6.dp))
            Text(text = "状态: ${entry.status}", color = statusColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onSave(entry.copy(title = title, content = content)) }) { Text("Save") }
            }
        }
    }
}
