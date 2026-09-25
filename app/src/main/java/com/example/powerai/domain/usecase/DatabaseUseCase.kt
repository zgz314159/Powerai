package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.DatabaseFileGroup
import com.example.powerai.domain.model.DatabaseRow
import com.example.powerai.core.model.ImportedFile
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.repository.SearchHistoryRepository
import com.example.powerai.util.PdfSourceRef
import javax.inject.Inject

/**
 * Use case encapsulating database query operations and history management.  This
 * allows the ViewModel to remain lightweight and easier to test.
 */
class DatabaseUseCase @Inject constructor(
    private val repository: KnowledgeRepository,
    private val historyRepository: SearchHistoryRepository
) {

    private val hex64Regex = Regex("^[0-9a-f]{64}$", RegexOption.IGNORE_CASE)

    suspend fun loadAll(): List<DatabaseFileGroup> {
        val importedFiles = repository.getImportedFiles()
        val entities = repository.getAll()
        return groupByFile(importedFiles, entities)
    }

    suspend fun search(query: String): List<DatabaseFileGroup> {
        val results = repository.searchByKeyword(query)
        val importedFiles = repository.getImportedFiles()
        return groupByFile(importedFiles, results)
            .sortedWith(
                compareByDescending<DatabaseFileGroup> { it.rows.size }
                    .thenBy { it.fileName }
            )
    }

    suspend fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val existing = historyRepository.loadHistory(HistoryScope.DATABASE)
        val deduped = existing.filter { it.query != trimmed }
        val newList = (listOf(SearchEntry(trimmed, now)) + deduped).take(50)
        historyRepository.saveHistory(HistoryScope.DATABASE, newList)
    }

    suspend fun clearSearchHistory() {
        historyRepository.clearHistory(HistoryScope.DATABASE)
    }

    suspend fun loadHistory(): List<SearchEntry> {
        return historyRepository.loadHistory(HistoryScope.DATABASE)
    }

    suspend fun resolveFileNameForItemId(entityId: Long): String? {
        val item = repository.getLocalItemById(entityId) ?: return null
        return PdfSourceRef.parse(item.source)?.fileName
    }

    suspend fun resolveDetailEntryNavigation(entityId: Long, query: String): com.example.powerai.domain.model.DetailEntryNavigation? {
        // Basic implementation for now to fix compilation
        return null
    }

    private fun groupByFile(
        importedFiles: List<ImportedFile>,
        items: List<KnowledgeItem>
    ): List<DatabaseFileGroup> {
        val byId = importedFiles.associateBy { it.fileId }
        val fileNameToFileId = importedFiles
            .filter { it.fileName.isNotBlank() }
            .associate { it.fileName to it.fileId }

        val grouped = LinkedHashMap<String, MutableList<KnowledgeItem>>()
        val groupMeta = LinkedHashMap<String, Pair<String?, String>>()

        for (item in items) {
            val rawSource = item.source.trim()
            val pdfRef = PdfSourceRef.parse(rawSource)

            val key: String
            val fileId: String?
            val fileName: String

            when {
                pdfRef != null -> {
                    key = pdfRef.fileName
                    fileId = byId[pdfRef.fileId]?.fileId ?: pdfRef.fileId
                    fileName = pdfRef.fileName
                }
                hex64Regex.matches(rawSource) -> {
                    val mapped = byId[rawSource]?.fileName?.trim().orEmpty()
                    val mappedDisplay = normalizeImportedDisplayName(mapped)
                    key = rawSource
                    fileId = rawSource
                    fileName = if (mappedDisplay.isNotBlank()) {
                        mappedDisplay
                    } else {
                        "文件(${rawSource.take(8)})"
                    }
                }
                rawSource.contains("::") -> {
                    val parts = rawSource.split("::")
                    fileName = parts.getOrNull(0) ?: ""
                    key = fileName
                    fileId = fileNameToFileId[fileName]
                }
                rawSource.isNotBlank() -> {
                    // For JSON/assets imports, source is often a path-like display string.
                    // Group by its tail segment to keep parent-child file view stable.
                    val normalized = rawSource.replace('\\', '/').trim('/')
                    val tail = normalized.substringAfterLast('/').ifBlank { normalized }
                    val display = tail.ifBlank { rawSource }
                    key = display
                    fileName = display
                    fileId = fileNameToFileId[display]
                }
                else -> {
                    key = "unknown"
                    fileId = null
                    fileName = "unknown"
                }
            }
            val list = grouped.getOrPut(key) { mutableListOf() }
            list.add(item)
            groupMeta.putIfAbsent(key, Pair(fileId, fileName))
        }

        return grouped.map { (k, list) ->
            val meta = groupMeta[k]
            val id = meta?.first
            val name = meta?.second ?: k

            val rows = list.map { item ->
                DatabaseRow(item, item.imagesCount)
            }
            val imageSum = rows.sumOf { it.imagesCount }
            DatabaseFileGroup(
                key = k,
                fileId = id,
                fileName = name,
                totalImages = imageSum,
                totalRowsCount = rows.size,
                totalImageCount = imageSum,
                rows = rows
            )
        }
    }
}

private fun normalizeImportedDisplayName(fileName: String): String {
    if (fileName.isBlank()) return ""
    val normalized = fileName.replace('\\', '/').trim('/').trim()
    if (normalized.isBlank()) return ""
    return when {
        normalized.endsWith("/knowledge_base.json", ignoreCase = true) ->
            normalized.removeSuffix("/knowledge_base.json").substringAfterLast('/').ifBlank { normalized }
        normalized.endsWith("knowledge_base.json", ignoreCase = true) ->
            normalized.removeSuffix("knowledge_base.json").trim('/').substringAfterLast('/').ifBlank { normalized }
        else -> normalized.substringAfterLast('/').ifBlank { normalized }
    }
}
