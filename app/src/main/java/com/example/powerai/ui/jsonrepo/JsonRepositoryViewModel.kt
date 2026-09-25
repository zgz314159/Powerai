package com.example.powerai.ui.jsonrepo

import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.json.JsonEntry
import com.example.powerai.data.json.JsonKnowledgeFile
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JsonRepoUiState(
    val files: List<JsonKnowledgeFile> = emptyList(),
    val fileStats: Map<String, Int> = emptyMap(),
    val entries: List<JsonEntry> = emptyList()
)

@HiltViewModel
class JsonRepositoryViewModel @Inject constructor(
    private val repo: JsonRepository
) : BaseMviViewModel<JsonRepoIntent, JsonRepoUiState, Nothing>(
    initialState = JsonRepoUiState()
) {

    val importProgress: StateFlow<ImportProgress?> = repo.importProgress

    private var currentFileId: String? = null

    override fun onIntent(intent: JsonRepoIntent) {
        when (intent) {
            is JsonRepoIntent.LoadFiles -> loadFiles()
            is JsonRepoIntent.SearchEntries -> searchEntries(intent.fileId, intent.keyword, intent.pageSize, intent.page)
            is JsonRepoIntent.SelectFile -> selectFile(intent.fileId, intent.pageSize, intent.page)
            is JsonRepoIntent.UpdateEntry -> updateEntry(intent.fileId, intent.entry)
            is JsonRepoIntent.ExportJson -> exportJson(intent.fileId, intent.targetPath, intent.callback)
            is JsonRepoIntent.ExportCsv -> exportCsv(intent.fileId, intent.targetPath, intent.callback)
        }
    }

    private fun loadFiles() {
        viewModelScope.launch {
            val files = repo.listFiles()
            val stats = mutableMapOf<String, Int>()
            files.forEach { f ->
                val entries = repo.getEntries(f.fileId)
                stats[f.fileId] = entries.count { it.status == "parsed" }
            }
            updateState { copy(files = files, fileStats = stats) }
        }
    }

    private fun searchEntries(fileId: String, keyword: String, pageSize: Int, page: Int) {
        currentFileId = fileId
        viewModelScope.launch {
            val all = repo.getEntries(fileId)
            val filtered = if (keyword.isBlank()) all else all.filter { it.title.contains(keyword, true) || it.content.contains(keyword, true) }
            val offset = page * pageSize
            val entries = if (offset >= filtered.size) emptyList() else filtered.subList(offset, kotlin.math.min(filtered.size, offset + pageSize))
            updateState { copy(entries = entries) }
        }
    }

    private fun selectFile(fileId: String, pageSize: Int, page: Int) {
        currentFileId = fileId
        viewModelScope.launch {
            val offset = page * pageSize
            val entries = repo.getEntriesPaged(fileId, offset, pageSize)
            updateState { copy(entries = entries) }
        }
    }

    private fun updateEntry(fileId: String, entry: JsonEntry) {
        viewModelScope.launch {
            val ok = repo.updateEntry(fileId, entry)
            if (ok) {
                currentFileId?.let { selectFile(it, 100, 0) }
            }
        }
    }

    private fun exportJson(fileId: String, targetPath: java.io.File, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.exportJson(fileId, targetPath)
            callback(ok)
        }
    }

    private fun exportCsv(fileId: String, targetPath: java.io.File, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.exportCsv(fileId, targetPath)
            callback(ok)
        }
    }
}
