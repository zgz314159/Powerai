@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.data.knowledge

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.data.importer.DocxParser
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.importer.PdfParser
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.importer.TxtParser
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import javax.inject.Inject

/**
 * File-based knowledge repository that stores JSON files under filesDir/knowledge_json
 * and exposes import/search/edit/export capabilities.
 */
class KnowledgeRepository @Inject constructor(private val filesDir: File) {

    private val gson = Gson()

    private fun storageDir(): File {
        val dir = File(filesDir, "knowledge_json")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    data class KnowledgeEntry(
        val id: String,
        val title: String = "",
        val content: String = "",
        val category: String = "",
        val source: String = "",
        val status: String = "parsed"
    )

    data class KnowledgeFile(
        val fileId: String,
        val fileName: String,
        val importTimestamp: Long,
        val entries: List<KnowledgeEntry>
    )

    suspend fun listFiles(): List<KnowledgeFile> = withContext(Dispatchers.IO) {
        val dir = storageDir()
        dir.listFiles()?.filter { it.isFile && it.extension == "json" }?.mapNotNull { f ->
            try {
                val text = f.readText()
                gson.fromJson(text, KnowledgeFile::class.java)
            } catch (_: Throwable) {
                null
            }
        } ?: emptyList()
    }

    suspend fun getEntries(fileId: String): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        val dir = storageDir()
        val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == fileId } ?: return@withContext emptyList()
        try {
            val kf = gson.fromJson(f.readText(), KnowledgeFile::class.java)
            kf.entries
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun search(query: String): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.lowercase()
        val out = mutableListOf<KnowledgeEntry>()
        listFiles().forEach { kf ->
            kf.entries.forEach { e ->
                if (e.title.lowercase().contains(q) || e.content.lowercase().contains(q) || e.category.lowercase().contains(q)) {
                    out.add(e)
                }
            }
        }
        out
    }

    /**
     * Update an entry inside a JSON file and write back atomically.
     */
    suspend fun updateEntry(fileId: String, updated: KnowledgeEntry): Boolean = withContext(Dispatchers.IO) {
        val dir = storageDir()
        val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == fileId } ?: return@withContext false
        try {
            val kf = gson.fromJson(f.readText(), KnowledgeFile::class.java)
            val idx = kf.entries.indexOfFirst { it.id == updated.id }
            if (idx < 0) return@withContext false
            val mutable = kf.entries.toMutableList()
            mutable[idx] = updated
            val newKf = kf.copy(entries = mutable.toList())
            val tmp = File.createTempFile("kb_", ".tmp", dir)
            OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8).use { it.write(gson.toJson(newKf)) }
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun exportJson(fileId: String, target: File): Boolean = withContext(Dispatchers.IO) {
        val dir = storageDir()
        val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == fileId } ?: return@withContext false
        try {
            f.copyTo(target, overwrite = true)
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun exportCsv(fileId: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val entries = getEntries(fileId)
            val sb = StringBuilder()
            sb.append("id,title,content,category,source,status\n")
            entries.forEach { e ->
                fun esc(s: String) = '"' + s.replace("\"", "\"\"") + '"'
                sb.append("${esc(e.id)},${esc(e.title)},${esc(e.content)},${esc(e.category)},${esc(e.source)},${esc(e.status)}\n")
            }
            target.writeText(sb.toString())
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Stream import a SAF Uri. Emits ImportProgress through returned Flow.
     * Uses available parsers (TxtParser/PdfParser/DocxParser) and TextSanitizer.
     */
    fun importUriFlow(uri: Uri, contentResolver: ContentResolver, displayName: String, batchSize: Int = com.example.powerai.data.importer.ImportDefaults.DEFAULT_BATCH_SIZE): Flow<ImportProgress> = flow {
        try {
            val lower = displayName.lowercase()
            var idCounter = 1L
            val tempEntries = mutableListOf<KnowledgeEntry>()
            var totalImported = 0L

            // progress emission handled via direct emit calls in onBatch

            val onBatch: suspend (List<com.example.powerai.data.local.entity.KnowledgeEntity>) -> Unit = { batch ->
                batch.forEach { be ->
                    val clean = TextSanitizer.sanitizeText(be.content)
                    val entry = KnowledgeEntry(id = idCounter.toString(), title = be.title, content = clean, category = be.category, source = be.source, status = "parsed")
                    tempEntries.add(entry)
                    idCounter++
                    totalImported++
                }
                // emit progress
                emit(ImportProgress(fileId = "", fileName = displayName, totalItems = null, importedItems = totalImported, percent = 0, status = "in_progress"))
            }

            val fileId = when {
                lower.endsWith(".txt") -> TxtParser(contentResolver).parse(uri, displayName, batchSize, onBatch)
                lower.endsWith(".pdf") -> PdfParser(contentResolver).parse(uri, displayName, batchSize, onBatch)
                lower.endsWith(".docx") || lower.endsWith(".doc") -> DocxParser(contentResolver).parse(uri, displayName, batchSize, onBatch)
                else -> throw IllegalArgumentException("Unsupported file type")
            }

            // Deduplicate: if file exists with same fileId skip
            val dir = storageDir()
            val finalFile = File(dir, "$fileId.json")
            if (finalFile.exists()) {
                emit(ImportProgress(fileId = fileId, fileName = displayName, totalItems = tempEntries.size.toLong(), importedItems = 0, percent = 100, status = "skipped", message = "already imported"))
                return@flow
            }

            // write JSON
            val kf = KnowledgeFile(fileId = fileId, fileName = displayName, importTimestamp = System.currentTimeMillis(), entries = tempEntries.toList())
            val tmp = File.createTempFile("kbimp_", ".tmp", dir)
            OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8).use { it.write(gson.toJson(kf)) }
            tmp.copyTo(finalFile, overwrite = true)
            tmp.delete()

            emit(ImportProgress(fileId = fileId, fileName = displayName, totalItems = tempEntries.size.toLong(), importedItems = tempEntries.size.toLong(), percent = 100, status = "imported"))
        } catch (e: Exception) {
            emit(ImportProgress(fileId = "", fileName = displayName, totalItems = null, importedItems = 0, percent = 0, status = "failed", message = e.message))
        }
    }

    private fun computeFileIdForUri(content: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
