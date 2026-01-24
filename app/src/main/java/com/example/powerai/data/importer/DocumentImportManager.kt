package com.example.powerai.data.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.domain.repository.KnowledgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val fileId: String,
    val fileName: String,
    val totalItems: Long?,
    val importedItems: Long,
    val percent: Int,
    val status: String,
    val message: String? = null
)

@Singleton
class DocumentImportManager @Inject constructor(
    private val context: Context,
    private val repo: KnowledgeRepository
) {

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun importUri(uri: Uri, batchSize: Int = 100) {
        scope.launch {
            val resolver = context.contentResolver
            val fileName = uri.lastPathSegment ?: "imported"
            try {
                // choose parser
                val lower = fileName.lowercase()
                val fileId = when {
                    lower.endsWith(".txt") -> {
                        val parser = TxtParser(resolver)
                        var imported = 0L
                        val id = parser.parse(uri, fileName, batchSize, onBatchReady = { batch ->
                            // map to domain items and insert
                            val items = batch.map { e ->
                                com.example.powerai.domain.model.KnowledgeItem(
                                    id = 0L,
                                    title = e.title,
                                    content = e.content,
                                    source = e.source,
                                    category = e.category,
                                    keywords = emptyList()
                                )
                            }
                            repo.insertBatch(items)
                            imported += batch.size
                            _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = imported, percent = 0, status = "in_progress")
                        }, onProgressBytes = { read, total ->
                            // no-op for now
                        })
                        id
                    }
                    lower.endsWith(".pdf") -> {
                        val parser = PdfParser(resolver)
                        var imported = 0L
                        val id = parser.parse(uri, fileName, batchSize, onBatchReady = { batch ->
                            val items = batch.map { e ->
                                com.example.powerai.domain.model.KnowledgeItem(
                                    id = 0L,
                                    title = e.title,
                                    content = e.content,
                                    source = e.source,
                                    category = e.category,
                                    keywords = emptyList()
                                )
                            }
                            repo.insertBatch(items)
                            imported += batch.size
                            _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = imported, percent = 0, status = "in_progress")
                        }, onProgressPages = { page, total ->
                            // update percent
                            val percent = if (total > 0) (page * 100 / total) else 0
                            _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = total.toLong(), importedItems = page.toLong(), percent = percent, status = "in_progress")
                        })
                        id
                    }
                    lower.endsWith(".docx") || lower.endsWith(".doc") -> {
                        val parser = DocxParser(resolver)
                        var imported = 0L
                        val id = parser.parse(uri, fileName, batchSize, onBatchReady = { batch ->
                            val items = batch.map { e ->
                                com.example.powerai.domain.model.KnowledgeItem(
                                    id = 0L,
                                    title = e.title,
                                    content = e.content,
                                    source = e.source,
                                    category = e.category,
                                    keywords = emptyList()
                                )
                            }
                            repo.insertBatch(items)
                            imported += batch.size
                            _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = imported, percent = 0, status = "in_progress")
                        })
                        id
                    }
                    else -> throw IllegalArgumentException("Unsupported file type: $fileName")
                }

                // dedupe check and mark
                val already = repo.isFileImported(fileId)
                if (already) {
                    _progress.value = ImportProgress(fileId = fileId, fileName = fileName, totalItems = null, importedItems = 0, percent = 100, status = "skipped", message = "already imported")
                } else {
                    repo.markFileImported(fileId, fileName, System.currentTimeMillis(), "imported")
                    _progress.value = ImportProgress(fileId = fileId, fileName = fileName, totalItems = null, importedItems = 0, percent = 100, status = "imported")
                }

            } catch (e: Exception) {
                _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = 0, percent = 0, status = "failed", message = e.message)
            }
        }
    }
}
