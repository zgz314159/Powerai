package com.example.powerai.ui.jsonrepo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.json.JsonEntry
import com.example.powerai.data.json.JsonKnowledgeFile
import com.example.powerai.data.json.JsonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JsonRepositoryViewModel @Inject constructor(private val repo: JsonRepository) : ViewModel() {

    private val _files = MutableStateFlow<List<JsonKnowledgeFile>>(emptyList())
    val files: StateFlow<List<JsonKnowledgeFile>> = _files

    private val _fileStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val fileStats: StateFlow<Map<String, Int>> = _fileStats

    private val _entries = MutableStateFlow<List<JsonEntry>>(emptyList())
    val entries: StateFlow<List<JsonEntry>> = _entries

    val importProgress: StateFlow<ImportProgress?> = repo.importProgress

    private var currentFileId: String? = null

    fun loadFiles() {
        viewModelScope.launch {
            _files.value = repo.listFiles()
            // precompute parsed counts
            val stats = mutableMapOf<String, Int>()
            _files.value.forEach { f ->
                val entries = repo.getEntries(f.fileId)
                stats[f.fileId] = entries.count { it.status == "parsed" }
            }
            _fileStats.value = stats
        }
    }

    fun searchEntries(fileId: String, keyword: String, pageSize: Int = 100, page: Int = 0) {
        currentFileId = fileId
        viewModelScope.launch {
            val all = repo.getEntries(fileId)
            val filtered = if (keyword.isBlank()) all else all.filter { it.title.contains(keyword, true) || it.content.contains(keyword, true) }
            val offset = page * pageSize
            _entries.value = if (offset >= filtered.size) emptyList() else filtered.subList(offset, kotlin.math.min(filtered.size, offset + pageSize))
        }
    }

    fun exportJson(fileId: String, targetPath: java.io.File, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.exportJson(fileId, targetPath)
            callback(ok)
        }
    }

    fun selectFile(fileId: String, pageSize: Int = 100, page: Int = 0) {
        currentFileId = fileId
        viewModelScope.launch {
            val offset = page * pageSize
            _entries.value = repo.getEntriesPaged(fileId, offset, pageSize)
        }
    }

    fun updateEntry(fileId: String, entry: JsonEntry) {
        viewModelScope.launch {
            val ok = repo.updateEntry(fileId, entry)
            if (ok) {
                // refresh current page
                currentFileId?.let { selectFile(it) }
            }
        }
    }

    fun exportCsv(fileId: String, targetPath: java.io.File, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.exportCsv(fileId, targetPath)
            callback(ok)
        }
    }
}
