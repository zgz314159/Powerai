package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.domain.repository.KnowledgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.security.MessageDigest
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
    private val repo: KnowledgeRepository,
    private val dao: KnowledgeDao,
    private val observability: com.example.powerai.util.ObservabilityService
) {

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun listAssetFilesRecursive(root: String): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>(256)
        fun walk(path: String) {
            val children = try {
                context.assets.list(path)?.toList().orEmpty()
            } catch (_: Throwable) {
                emptyList()
            }
            if (children.isEmpty()) {
                // If AssetManager.list() returns empty, it can be either a file or an empty dir.
                // We treat it as a file candidate.
                out.add(path)
                return
            }
            for (name in children) {
                val child = if (path.isBlank()) name else "$path/$name"
                walk(child)
            }
        }
        walk(root)
        out
    }

    /**
     * One-time import of preprocessed JSON knowledge under assets/kb (recursive) into Room.
     *
     * This is the primary path for shipping a ready-to-search KB with the app.
     */
    suspend fun importAssetsIfNeed(assetRoot: String = "kb") {
        val files = listAssetFilesRecursive(assetRoot)
            .filter {
                val lower = it.lowercase()
                lower.endsWith(".json") && !lower.endsWith("/manifest.json")
            }

        if (files.isEmpty()) return

        val importer = StreamingJsonResourceImporter(dao)

        for ((index, assetPath) in files.withIndex()) {
            val fileName = assetPath.substringAfterLast('/')
            // Use asset path hash as a stable fileId for dedupe.
            val fileId = sha256Hex("asset:$assetPath")

            observability.importStarted(fileId, fileName)

            if (repo.isFileImported(fileId)) continue

            _progress.value = ImportProgress(
                fileId = fileId,
                fileName = fileName,
                totalItems = files.size.toLong(),
                importedItems = index.toLong(),
                percent = ((index * 100f) / files.size.toFloat()).toInt().coerceIn(0, 100),
                status = "in_progress",
                message = "importing assets: $assetPath"
            )

            try {
                context.assets.open(assetPath).use { input ->
                    importer.importFromJson(
                        inputStream = input,
                        batchSize = ImportDefaults.DEFAULT_BATCH_SIZE,
                        trace = null,
                        fallbackFileName = fileName,
                        fallbackFileId = fileId
                    ).collect { p ->
                        // Forward per-file progress into a single stream for UI/diagnostics.
                        _progress.value = p.copy(fileId = fileId, fileName = fileName)
                    }
                }
                repo.markFileImported(fileId, fileName, System.currentTimeMillis(), "imported")
                observability.importCompleted(fileId, fileName, /* importedItems not tracked here */ 0)
            } catch (t: Throwable) {
                repo.markFileImported(fileId, fileName, System.currentTimeMillis(), "failed")
                _progress.value = ImportProgress(
                    fileId = fileId,
                    fileName = fileName,
                    totalItems = files.size.toLong(),
                    importedItems = index.toLong(),
                    percent = ((index * 100f) / files.size.toFloat()).toInt().coerceIn(0, 100),
                    status = "failed",
                    message = t.message
                )
                observability.importFailed(fileId, fileName, t.message)
            }
        }
    }

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
                        }, onProgressBytes = { _, _ ->
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

                observability.importStarted(fileId, fileName)
                // dedupe check and mark
                val already = repo.isFileImported(fileId)
                if (already) {
                    _progress.value = ImportProgress(fileId = fileId, fileName = fileName, totalItems = null, importedItems = 0, percent = 100, status = "skipped", message = "already imported")
                } else {
                    repo.markFileImported(fileId, fileName, System.currentTimeMillis(), "imported")
                    _progress.value = ImportProgress(fileId = fileId, fileName = fileName, totalItems = null, importedItems = 0, percent = 100, status = "imported")
                    observability.importCompleted(fileId, fileName, 0)
                }
            } catch (e: Exception) {
                _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = 0, percent = 0, status = "failed", message = e.message)
                observability.importFailed("", fileName, e.message)
            }
        }
    }
}
