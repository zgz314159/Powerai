package com.example.powerai.ui.screen.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.knowledge.KnowledgeRepository
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.data.importer.ImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
    private val hybrid: HybridQueryUseCase
) : ViewModel() {

    private val _fileList = MutableStateFlow<List<KnowledgeRepository.KnowledgeFile>>(emptyList())
    val fileList: StateFlow<List<KnowledgeRepository.KnowledgeFile>> = _fileList

    private val _entries = MutableStateFlow<List<KnowledgeRepository.KnowledgeEntry>>(emptyList())
    val entries: StateFlow<List<KnowledgeRepository.KnowledgeEntry>> = _entries

    private val _searchResults = MutableStateFlow<List<KnowledgeRepository.KnowledgeEntry>>(emptyList())
    val searchResults: StateFlow<List<KnowledgeRepository.KnowledgeEntry>> = _searchResults

    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress

    private val _fontSize = MutableStateFlow(1.0f)
    val fontSize: StateFlow<Float> = _fontSize

    // paging
    private var currentAll: List<KnowledgeRepository.KnowledgeEntry> = emptyList()
    var currentPage = 0
        private set
    var pageSize = 50
        private set

    init {
        refreshFiles()
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _fileList.value = repo.listFiles()
        }
    }

    fun loadEntries(fileId: String) {
        viewModelScope.launch {
            val e = repo.getEntries(fileId)
            currentAll = e
            currentPage = 0
            _entries.value = pageSlice()
        }
    }

    private fun pageSlice(): List<KnowledgeRepository.KnowledgeEntry> {
        val from = currentPage * pageSize
        if (from >= currentAll.size) return emptyList()
        val to = kotlin.math.min(currentAll.size, from + pageSize)
        return currentAll.subList(from, to)
    }

    fun nextPage() { viewModelScope.launch { loadPage(currentPage + 1) } }
    fun prevPage() { viewModelScope.launch { if (currentPage > 0) loadPage(currentPage - 1) } }

    fun loadPage(page: Int) {
        currentPage = page
        _entries.value = pageSlice()
    }

    fun search(keyword: String) {
        _searchKeyword.value = keyword
        viewModelScope.launch {
            if (keyword.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            val res = hybrid.hybridQuery(keyword)
            _searchResults.value = res
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size.coerceIn(0.75f, 2.0f)
    }

    fun importFile(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            repo.importUriFlow(uri, context.contentResolver, displayName).collectLatest { prog ->
                _importProgress.value = prog
                if (prog.status == "imported" || prog.status == "skipped") {
                    // refresh files when done
                    refreshFiles()
                }
            }
        }
    }

    fun editEntry(fileId: String, entryId: String, newContent: String) {
        viewModelScope.launch {
            val entries = repo.getEntries(fileId).toMutableList()
            val idx = entries.indexOfFirst { it.id == entryId }
            if (idx >= 0) {
                val updated = entries[idx].copy(content = newContent)
                val ok = repo.updateEntry(fileId, updated)
                if (ok) {
                    // update local state
                    loadEntries(fileId)
                }
            }
        }
    }

    // Find the fileId that contains the entry with the given id (scans stored JSON files)
    private suspend fun findFileIdForEntry(entryId: String): String? {
        val files = repo.listFiles()
        files.forEach { f ->
            if (f.entries.any { it.id == entryId }) return f.fileId
        }
        return null
    }

    // Edit both title and content; if fileId is null/empty will attempt to locate the file containing the entry
    fun editEntryFull(maybeFileId: String?, entryId: String, newTitle: String, newContent: String) {
        viewModelScope.launch {
            val fid = if (!maybeFileId.isNullOrBlank()) maybeFileId else findFileIdForEntry(entryId)
            if (fid.isNullOrBlank()) return@launch
            val entries = repo.getEntries(fid).toMutableList()
            val idx = entries.indexOfFirst { it.id == entryId }
            if (idx >= 0) {
                val updated = entries[idx].copy(title = newTitle, content = newContent)
                val ok = repo.updateEntry(fid, updated)
                if (ok) {
                    loadEntries(fid)
                }
            }
        }
    }
}
