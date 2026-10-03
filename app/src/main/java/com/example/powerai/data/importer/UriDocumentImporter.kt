package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.ObservabilityService
import com.example.powerai.core.repository.KnowledgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Imports a user-picked document URI (SAF/ContentResolver) in batches through [FileParser].
 *
 * Extracted from [DocumentImportManager] so asset-package import and URI import stay separate
 * responsibilities; progress is reported back through [onProgress].
 */
@Suppress("LongParameterList")
internal class UriDocumentImporter(
    private val context: Context,
    private val repo: KnowledgeRepository,
    private val observability: ObservabilityService,
    private val scope: CoroutineScope,
    private val parserFactory: FileParserFactoryType,
    private val onProgress: (ImportProgress) -> Unit,
) {
    @Suppress("TooGenericExceptionCaught")
    fun import(
        uri: Uri,
        batchSize: Int = 100,
    ) {
        scope.launch {
            val resolver = context.contentResolver
            val fileName = uri.lastPathSegment ?: "imported"
            try {
                val parser = parserFactory.create(fileName, resolver)
                var imported = 0L
                val fileId =
                    parser.parse(uri, fileName, batchSize, onBatchReady = { batch ->
                        val items =
                            batch.map { e ->
                                KnowledgeItem(
                                    id = 0L,
                                    title = e.title,
                                    content = e.content,
                                    source = e.source,
                                    category = e.category,
                                    keywords = emptyList(),
                                )
                            }
                        repo.insertBatch(items)
                        imported += batch.size
                        onProgress(
                            ImportProgress(
                                fileId = "",
                                fileName = fileName,
                                totalItems = null,
                                importedItems = imported,
                                percent = 0,
                                status = "in_progress",
                            ),
                        )
                    })

                observability.importStarted(fileId, fileName)
                if (repo.isFileImported(fileId)) {
                    onProgress(
                        ImportProgress(
                            fileId = fileId,
                            fileName = fileName,
                            totalItems = null,
                            importedItems = 0,
                            percent = 100,
                            status = "skipped",
                            message = "already imported",
                        ),
                    )
                } else {
                    repo.markFileImported(fileId, fileName, System.currentTimeMillis(), "imported")
                    onProgress(
                        ImportProgress(
                            fileId = fileId,
                            fileName = fileName,
                            totalItems = null,
                            importedItems = 0,
                            percent = 100,
                            status = "imported",
                        ),
                    )
                    observability.importCompleted(fileId, fileName, 0)
                }
            } catch (e: Exception) {
                onProgress(
                    ImportProgress(
                        fileId = "",
                        fileName = fileName,
                        totalItems = null,
                        importedItems = 0,
                        percent = 0,
                        status = "failed",
                        message = e.message,
                    ),
                )
                observability.importFailed("", fileName, e.message)
            }
        }
    }
}
