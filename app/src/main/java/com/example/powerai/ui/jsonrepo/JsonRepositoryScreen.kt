@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.jsonrepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.data.json.JsonEntry
import com.example.powerai.data.json.JsonKnowledgeFile
import kotlinx.coroutines.launch
import java.io.File

@Composable
@Suppress("UNUSED_PARAMETER")
fun JsonRepositoryScreen(navController: NavHostController, viewModel: JsonRepositoryViewModel) {
    val files by viewModel.files.collectAsState()
    val entries by viewModel.entries.collectAsState()

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
                LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
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
            LinearProgressIndicator(progress = { progressFloat }, modifier = Modifier.fillMaxWidth())
            Text("Import: ${p.status} ${p.importedItems}/${p.totalItems ?: "-"}")
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                val fid = selectedFileId ?: run {
                    coroutineScope.launch { snackbarHostState.showSnackbar("请选择一个文件以导出") }
                    return@Button
                }
                val target = File(context.filesDir, "export_$fid.csv")
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
                val target = File(context.filesDir, "export_$fid.json")
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
