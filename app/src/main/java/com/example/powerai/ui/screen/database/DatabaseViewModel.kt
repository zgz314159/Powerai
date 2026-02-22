package com.example.powerai.ui.screen.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.ImportedFileEntity
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.util.PdfSourceRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

data class DatabaseUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val groups: List<DatabaseFileGroup> = emptyList()
)

data class DatabaseFileGroup(
    val key: String,
    val fileId: String? = null,
    val fileName: String,
    val totalImages: Int,
    val rows: List<DatabaseRow>
)

data class DatabaseRow(
    val item: KnowledgeItem,
    val imagesCount: Int
)

@HiltViewModel
class DatabaseViewModel @Inject constructor(
    private val dao: KnowledgeDao,
    private val historyStore: com.example.powerai.data.local.DatabaseSearchHistoryStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DatabaseUiState())
    val uiState: StateFlow<DatabaseUiState> = _uiState.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<com.example.powerai.data.local.LocalSearchEntry>>(emptyList())
    val searchHistory: StateFlow<List<com.example.powerai.data.local.LocalSearchEntry>> = _searchHistory

    fun refresh() {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val importedFiles = try { dao.getImportedFiles() } catch (_: Throwable) { emptyList() }
                val entities = dao.getAll()
                val groups = groupByFile(importedFiles = importedFiles, entities = entities)
                _uiState.value = DatabaseUiState(isLoading = false, groups = groups)
            } catch (t: Throwable) {
                _uiState.value = DatabaseUiState(
                    isLoading = false,
                    errorMessage = t.message ?: "加载失败"
                )
            }
        }
    }

    fun search(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            loadAll()
            return
        }

        // record into history
        addSearchHistory(query)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val keywordResults = dao.searchByKeyword(query)
                val noSpace = query.filterNot { it.isWhitespace() }
                val noSpaceResults = if (noSpace.isNotBlank() && noSpace != query) {
                    dao.searchByKeywordNoSpace(noSpace)
                } else {
                    emptyList()
                }

                val merged = LinkedHashMap<Long, KnowledgeEntity>()
                for (e in keywordResults) merged[e.id] = e
                for (e in noSpaceResults) merged[e.id] = e

                val importedFiles = try { dao.getImportedFiles() } catch (_: Throwable) { emptyList() }
                val groups = groupByFile(importedFiles = importedFiles, entities = merged.values.toList())
                _uiState.value = DatabaseUiState(isLoading = false, groups = groups)
            } catch (t: Throwable) {
                _uiState.value = DatabaseUiState(
                    isLoading = false,
                    errorMessage = t.message ?: "搜索失败"
                )
            }
        }
    }

    private fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val deduped = _searchHistory.value.filter { it.query != trimmed }
        val newList = listOf(com.example.powerai.data.local.LocalSearchEntry(trimmed, now)) + deduped
        _searchHistory.value = if (newList.size > 50) newList.take(50) else newList
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyStore.save(_searchHistory.value)
            } catch (_: Throwable) {}
        }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try { historyStore.clear() } catch (_: Throwable) {}
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = historyStore.load()
                if (loaded.isNotEmpty()) _searchHistory.value = loaded
            } catch (_: Throwable) {}
        }
    }

    private fun groupByFile(
        importedFiles: List<ImportedFileEntity>,
        entities: List<KnowledgeEntity>
    ): List<DatabaseFileGroup> {
        val byId = importedFiles.associateBy { it.fileId }
        val fileNameToFileId = importedFiles
            .filter { it.fileName.isNotBlank() }
            .associate { it.fileName to it.fileId }

        val grouped = LinkedHashMap<String, MutableList<KnowledgeEntity>>()
        val groupMeta = LinkedHashMap<String, Pair<String?, String>>() // key -> (fileId?, fileName)

        for (e in entities) {
            val rawSource = e.source.trim()
            val pdfRef = PdfSourceRef.parse(rawSource)

            val key: String
            val fileId: String?
            val fileName: String

            when {
                pdfRef != null -> {
                    fileId = pdfRef.fileId
                    fileName = pdfRef.fileName
                    key = "pdf:${pdfRef.fileId}"
                }
                rawSource.length == 64 && rawSource.all { it.isLetterOrDigit() } && byId.containsKey(rawSource) -> {
                    fileId = rawSource
                    fileName = byId[rawSource]?.fileName?.takeIf { it.isNotBlank() } ?: rawSource
                    key = rawSource
                }
                rawSource.isNotBlank() && fileNameToFileId.containsKey(rawSource) -> {
                    fileId = fileNameToFileId[rawSource]
                    fileName = rawSource
                    key = "name::$rawSource"
                }
                else -> {
                    fileId = null
                    fileName = if (rawSource.isNotBlank()) PdfSourceRef.display(rawSource) else "未归类"
                    key = "other::$fileName"
                }
            }

            grouped.getOrPut(key) { ArrayList() }.add(e)
            groupMeta[key] = fileId to fileName
        }

        val groups = ArrayList<DatabaseFileGroup>(grouped.size)
        for ((key, list) in grouped) {
            val meta = groupMeta[key]
            val fileId = meta?.first
            val fileName = meta?.second.orEmpty()

            val sorted = list.sortedWith(compareBy<KnowledgeEntity>({ it.pageNumber ?: Int.MAX_VALUE }, { it.id }))
            var totalImages = 0
            val rows = sorted.map { entity ->
                val images = imagesCountOf(entity)
                totalImages += images
                DatabaseRow(item = entity.toDomain(), imagesCount = images)
            }

            groups.add(
                DatabaseFileGroup(
                    key = key,
                    fileId = fileId,
                    fileName = fileName,
                    totalImages = totalImages,
                    rows = rows
                )
            )
        }

        // Prefer imported_files order (timestamp desc). Unknown sources go last.
        val fileIdToRank = LinkedHashMap<String, Int>()
        importedFiles.forEachIndexed { idx, f -> fileIdToRank[f.fileId] = idx }

        return groups.sortedWith(
            compareBy<DatabaseFileGroup> { g ->
                val id = g.fileId
                if (id != null && fileIdToRank.containsKey(id)) fileIdToRank[id] ?: Int.MAX_VALUE else Int.MAX_VALUE
            }.thenBy { it.fileName }
        )
    }

    private fun imagesCountOf(entity: KnowledgeEntity): Int {
        val raw = entity.imageUris?.trim().orEmpty()
        if (raw.isBlank()) return 0
        return try {
            JSONArray(raw).length()
        } catch (_: Throwable) {
            0
        }
    }

    private fun KnowledgeEntity.toDomain(): KnowledgeItem = KnowledgeItem(
        id = this.id,
        title = this.title,
        content = this.content,
        source = this.source,
        pageNumber = this.pageNumber,
        category = this.category,
        keywords = if (this.keywordsSerialized.isBlank()) emptyList() else this.keywordsSerialized.split(',').map { it.trim() }
    )
}
