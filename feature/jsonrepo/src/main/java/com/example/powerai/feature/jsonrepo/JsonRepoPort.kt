package com.example.powerai.feature.jsonrepo

import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.data.json.JsonEntry
import com.example.powerai.data.json.JsonKnowledgeFile
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Narrow JSON knowledge-file surface for the JsonRepo feature (Phase C7).
 * Keeps file I/O / format parsers in :app JsonRepository.
 */
interface JsonRepoPort {
    val importProgress: StateFlow<ImportProgress?>

    suspend fun listFiles(): List<JsonKnowledgeFile>

    suspend fun getEntries(fileId: String): List<JsonEntry>

    suspend fun getEntriesPaged(fileId: String, offset: Int, limit: Int): List<JsonEntry>

    suspend fun updateEntry(fileId: String, updated: JsonEntry): Boolean

    suspend fun exportJson(fileId: String, target: File): Boolean

    suspend fun exportCsv(fileId: String, target: File): Boolean
}
