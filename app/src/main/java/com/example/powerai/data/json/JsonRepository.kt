package com.example.powerai.data.json

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.powerai.data.export.ExportUtils
import com.example.powerai.data.importer.DocxParser
import com.example.powerai.data.importer.PdfParser
import com.example.powerai.data.importer.TxtParser
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.lang.Exception
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class JsonEntry(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "",
    val source: String = "",
    val status: String = "parsed"
)

data class JsonKnowledgeFile(
    val fileId: String,
    val fileName: String,
    val importTimestamp: Long,
    val entriesCount: Int
)

@Singleton
class JsonRepository @Inject constructor(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress

    private fun storageDir(): File {
        val dir = File(context.filesDir, "knowledge_json")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun listFiles(): List<JsonKnowledgeFile> = withContext(Dispatchers.IO) {
        val dir = storageDir()
        dir.listFiles()?.filter { it.isFile && it.extension == "json" }?.map { f ->
            // read metadata quickly
            try {
                val text = f.readText()
                val obj = gson.fromJson(text, Map::class.java)
                val fileId = obj["fileId"] as? String ?: f.nameWithoutExtension
                val fileName = obj["fileName"] as? String ?: f.name
                val timestamp = (obj["importTimestamp"] as? Double)?.toLong() ?: f.lastModified()
                val entries = (obj["entries"] as? List<*>)
                val count = entries?.size ?: 0
                JsonKnowledgeFile(fileId = fileId, fileName = fileName, importTimestamp = timestamp, entriesCount = count)
            } catch (t: Throwable) {
                JsonKnowledgeFile(fileId = f.nameWithoutExtension, fileName = f.name, importTimestamp = f.lastModified(), entriesCount = 0)
            }
        } ?: emptyList()
    }

    suspend fun getEntries(fileId: String): List<JsonEntry> = withContext(Dispatchers.IO) {
        val dir = storageDir()
        val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == fileId } ?: return@withContext emptyList()
        try {
            val map = gson.fromJson(f.readText(), Map::class.java) as? Map<*, *> ?: return@withContext emptyList()
            val entriesAny = map["entries"] as? List<*> ?: return@withContext emptyList()
            entriesAny.mapNotNull { e ->
                val m = e as? Map<*, *>
                m?.let {
                    JsonEntry(
                        id = it["id"]?.toString() ?: "",
                        title = it["title"]?.toString() ?: "",
                        content = it["content"]?.toString() ?: "",
                        category = it["category"]?.toString() ?: "",
                        source = it["source"]?.toString() ?: "",
                        status = it["status"]?.toString() ?: "parsed"
                    )
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    suspend fun getEntriesPaged(fileId: String, offset: Int, limit: Int): List<JsonEntry> = withContext(Dispatchers.IO) {
        val all = getEntries(fileId)
        if (offset >= all.size) return@withContext emptyList()
        val end = kotlin.math.min(all.size, offset + limit)
        return@withContext all.subList(offset, end)
    }

    suspend fun updateEntry(fileId: String, updated: JsonEntry): Boolean = withContext(Dispatchers.IO) {
        val dir = storageDir()
        val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == fileId } ?: return@withContext false
        try {
            val rootAny = gson.fromJson(f.readText(), Map::class.java) as? Map<*, *> ?: return@withContext false
            val entriesAny = rootAny["entries"] as? List<*> ?: return@withContext false
            val mutableEntries = entriesAny.map { it as? Map<*, *> ?: emptyMap<Any, Any>() }.toMutableList()
            val idx = mutableEntries.indexOfFirst { (it["id"]?.toString() ?: "") == updated.id }
            if (idx >= 0) {
                val map = mapOf<String, Any>(
                    "id" to updated.id,
                    "title" to updated.title,
                    "content" to updated.content,
                    "category" to updated.category,
                    "source" to updated.source,
                    "status" to updated.status
                )
                mutableEntries[idx] = map
                // build new root map
                val newRoot = HashMap<String, Any?>()
                rootAny.forEach { (k, v) -> if (k is String) newRoot[k] = v }
                newRoot["entries"] = mutableEntries
                f.writeText(gson.toJson(newRoot))
                return@withContext true
            }
            return@withContext false
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun exportCsv(fileId: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val entries = getEntries(fileId)
            val gsonItems = entries.map { e ->
                KnowledgeEntity(id = 0L, title = e.title, content = e.content, source = e.source, category = e.category)
            }
            val csv = ExportUtils.toCsv(gsonItems)
            target.writeText(csv)
            true
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun exportJson(fileId: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = storageDir()
            val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == fileId } ?: return@withContext false
            f.copyTo(target, overwrite = true)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun tempFileFor(fileName: String): File {
        val dir = storageDir()
        return File.createTempFile("import_", ".tmp", dir)
    }

    suspend fun importUri(uri: Uri, contentResolver: ContentResolver, displayName: String, batchSize: Int = 100) = withContext(Dispatchers.IO) {
        try {
            val lower = displayName.lowercase()
            val temp = tempFileFor(displayName)
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(temp), Charsets.UTF_8))
            // write header
            val importTimestamp = System.currentTimeMillis()
            val md = MessageDigest.getInstance("SHA-256")

            writer.write("{\n")
            writer.write("\"fileId\":\"TEMP\",\n")
            writer.write("\"fileName\":\"${displayName}\",\n")
            writer.write("\"importTimestamp\":$importTimestamp,\n")
            writer.write("\"entries\":[\n")

            var first = true
            var idCounter = 1
            var totalImported = 0L

            val onBatch: suspend (List<KnowledgeEntity>) -> Unit = { batch ->
                batch.forEach { e ->
                    val clean = TextSanitizer.sanitizeText(e.content)
                    val entry = JsonEntry(id = idCounter.toString(), title = e.title, content = clean, category = e.category, source = e.source, status = "parsed")
                    val json = gson.toJson(entry)
                    if (!first) writer.write(",\n")
                    writer.write(json)
                    first = false
                    idCounter++
                    totalImported++
                    _importProgress.value = ImportProgress(fileId = "", fileName = displayName, totalItems = null, importedItems = totalImported, percent = 0, status = "in_progress")
                }
                writer.flush()
            }

            val fileId = when {
                lower.endsWith(".txt") -> {
                    val parser = TxtParser(contentResolver)
                    parser.parse(uri, displayName, batchSize, onBatch)
                }
                lower.endsWith(".pdf") -> {
                    val parser = PdfParser(contentResolver)
                    parser.parse(uri, displayName, batchSize, onBatch, onProgressPages = { p, t ->
                        val percent = if (t > 0) (p * 100 / t) else 0
                        _importProgress.value = ImportProgress(fileId = "", fileName = displayName, totalItems = t.toLong(), importedItems = p.toLong(), percent = percent, status = "in_progress")
                    })
                }
                lower.endsWith(".docx") || lower.endsWith(".doc") -> {
                    val parser = DocxParser(contentResolver)
                    parser.parse(uri, displayName, batchSize, onBatch)
                }
                else -> throw IllegalArgumentException("Unsupported file type")
            }

            // close entries array
            writer.write("\n],\n")
            writer.write("\"entriesCount\":$totalImported\n")
            writer.write("}\n")
            writer.close()

            // replace TEMP fileId with real fileId and rename atomically
            val finalFile = File(storageDir(), "$fileId.json")
            // replace inside file
            val content = temp.readText()
            val fixed = content.replaceFirst("\"fileId\":\"TEMP\"", "\"fileId\":\"$fileId\"")
            finalFile.writeText(fixed)
            temp.delete()

            _importProgress.value = ImportProgress(fileId = fileId, fileName = displayName, totalItems = totalImported, importedItems = totalImported, percent = 100, status = "imported")
            fileId
        } catch (e: Exception) {
            _importProgress.value = ImportProgress(fileId = "", fileName = displayName, totalItems = null, importedItems = 0, percent = 0, status = "failed", message = e.message)
            // Swallow exception to avoid crashing the application; return empty fileId to indicate failure.
            return@withContext ""
        }
    }
}
