package com.example.powerai.data.saf

import android.content.Context
import android.net.Uri
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.repository.KnowledgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import kotlin.math.roundToInt

data class ImportProgress(
    val fileId: String,
    val fileName: String,
    val totalItems: Int,
    val importedItems: Int,
    val percent: Int,
    val status: String,
    val message: String? = null
)

class StreamingDocumentImporter(private val context: Context, private val repo: KnowledgeRepository) {
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress

    /**
     * Import a single Uri. This method streams the file content, computes a fileId hash,
     * parses into KnowledgeItem in chunks, and calls repository.insertBatch periodically.
     * It updates `progress` StateFlow with percentage and counts.
     */
    fun importUri(uri: Uri, batchSize: Int = 100) {
        CoroutineScope(Dispatchers.IO).launch {
            val resolver = context.contentResolver
            val displayName = resolver.getType(uri) ?: uri.lastPathSegment ?: "unknown"
            var fileId = ""
            try {
                // First pass: compute hash and stream-parse
                resolver.openInputStream(uri)?.use { input ->
                    val bis = BufferedInputStream(input)
                    val digest = MessageDigest.getInstance("SHA-256")
                    // We'll try to detect TXT by simple heuristic: first bytes are readable
                    val reader = BufferedReader(InputStreamReader(bis, Charsets.UTF_8))
                    val bufferItems = mutableListOf<KnowledgeItem>()
                    var lineCount = 0
                    var importedCount = 0
                    var totalReadBytes = 0L
                    var totalBytesEstimate = resolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L

                    // compute hash while reading lines
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val bytes = line.toByteArray(Charsets.UTF_8)
                        digest.update(bytes)
                        totalReadBytes += bytes.size
                        // create item per line (or paragraph). Title uses file name and line index.
                        val item = KnowledgeItem(
                            id = 0L,
                            title = (uri.lastPathSegment ?: "imported") + " - ${lineCount + 1}",
                            content = line,
                            source = uri.toString(),
                            category = "",
                            keywords = emptyList()
                        )
                        bufferItems.add(item)
                        lineCount++

                        if (bufferItems.size >= batchSize) {
                            repo.insertBatch(bufferItems)
                            importedCount += bufferItems.size
                            bufferItems.clear()
                            val percent = if (totalBytesEstimate > 0) ((totalReadBytes.toDouble() / totalBytesEstimate.toDouble()) * 100).roundToInt() else -1
                            _progress.value = ImportProgress(fileId = "", fileName = displayName, totalItems = -1, importedItems = importedCount, percent = percent, status = "in_progress")
                        }
                        line = reader.readLine()
                    }
                    // flush remaining
                    if (bufferItems.isNotEmpty()) {
                        repo.insertBatch(bufferItems)
                        importedCount += bufferItems.size
                        bufferItems.clear()
                    }
                    // finalize fileId from digest
                    fileId = digest.digest().joinToString("") { "%02x".format(it) }
                    // mark as imported
                    repo.markFileImported(fileId, uri.lastPathSegment ?: "unknown", System.currentTimeMillis(), "imported")
                    _progress.value = ImportProgress(fileId = fileId, fileName = displayName, totalItems = lineCount, importedItems = importedCount, percent = 100, status = "completed")
                } ?: run {
                    _progress.value = ImportProgress(fileId = "", fileName = displayName, totalItems = 0, importedItems = 0, percent = 0, status = "failed", message = "Cannot open stream")
                }
            } catch (t: Throwable) {
                _progress.value = ImportProgress(fileId = fileId, fileName = displayName, totalItems = -1, importedItems = 0, percent = 0, status = "failed", message = t.message)
            }
        }
    }
}

/**
 * 只负责读取指定Uri的文本内容。
 * 不解析结构、不存数据库、不引用UI。
 */
object DocumentImportManager {
    /**
     * 读取文件文本内容
     */
    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: ""
    }
}

