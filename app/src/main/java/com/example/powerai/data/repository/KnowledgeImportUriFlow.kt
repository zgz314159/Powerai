package com.example.powerai.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
// android.util.Log removed per TODO order; use TraceLogger if needed
import com.example.powerai.data.importer.BlocksPreprocessor
import com.example.powerai.data.importer.FileParserFactory
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.domain.model.KnowledgeEntry
import com.example.powerai.domain.model.KnowledgeFile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

internal object KnowledgeImportUriFlow {

    private data class ImportCounters(
        var idCounter: Long,
        var totalImported: Long
    )

    fun importUriFlow(
        context: Context,
        dao: KnowledgeDao,
        gson: Gson,
        storageDir: () -> File,
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
        batchSize: Int
    ): Flow<ImportProgress> = channelFlow {
        // log removed: Flow object created for $displayName
        // log removed: !!! Repository 收到导入请求 !!!

        withContext(Dispatchers.IO) {
            try {
                // log removed: importUriFlow start: displayName=$displayName uri=$uri batchSize=$batchSize
                val lower = displayName.lowercase()
                val counters = ImportCounters(idCounter = 1L, totalImported = 0L)
                val tempEntries = mutableListOf<KnowledgeEntry>()

                val onBatch: suspend (List<KnowledgeEntity>) -> Unit = { batch ->
                    processBatch(
                        dao = dao,
                        batch = batch,
                        fileName = displayName,
                        tempEntries = tempEntries,
                        counters = counters,
                        emit = { send(it) }
                    )
                }

                takePersistableReadPermission(context, uri)

                // log removed: Starting parsing for displayName=$displayName
                val mimeType = try {
                    context.contentResolver.getType(uri)
                } catch (_: Exception) {
                    null
                }
                // log removed: parsing: displayName=$displayName lower=$lower mimeType=$mimeType uri=$uri
                // common parser selection
                val parser = FileParserFactory.create(displayName, contentResolver)
                val fileId = parser.parse(uri, displayName, batchSize, onBatch)

                // log removed: Parsing finished, fileId=$fileId

                val dir = storageDir()
                val finalFile = File(dir, "$fileId.json")
                if (finalFile.exists()) {
                    // log removed: File already imported: $fileId
                    send(
                        ImportProgress(
                            fileId = fileId,
                            fileName = displayName,
                            totalItems = tempEntries.size.toLong(),
                            importedItems = 0,
                            percent = 100,
                            status = "skipped",
                            message = "already imported"
                        )
                    )
                    return@withContext
                }

                val kf = KnowledgeFile(
                    fileId = fileId,
                    fileName = displayName,
                    importTimestamp = System.currentTimeMillis(),
                    entries = tempEntries.toList()
                )
                writeKnowledgeFile(gson, kf, finalFile)

                // log removed: Import completed for fileId=$fileId entries=${tempEntries.size}
                send(
                    ImportProgress(
                        fileId = fileId,
                        fileName = displayName,
                        totalItems = tempEntries.size.toLong(),
                        importedItems = tempEntries.size.toLong(),
                        percent = 100,
                        status = "imported"
                    )
                )
            } catch (e: Exception) {
                // log removed: importUriFlow failed
                send(
                    ImportProgress(
                        fileId = "",
                        fileName = displayName,
                        totalItems = null,
                        importedItems = 0,
                        percent = 0,
                        status = "failed",
                        message = e.message
                    )
                )
            }
        }
    }

    private suspend fun processBatch(
        dao: KnowledgeDao,
        batch: List<KnowledgeEntity>,
        fileName: String,
        tempEntries: MutableList<KnowledgeEntry>,
        counters: ImportCounters,
        emit: suspend (ImportProgress) -> Unit
    ) {
        try {
            val enriched = batch.map { be ->
                if (!be.contentBlocksJson.isNullOrBlank()) return@map be
                val blocksJson = BlocksPreprocessor.blocksJsonFromPlainText(be.content) ?: return@map be
                val normalized = BlocksPreprocessor.normalizedForSearchFromBlocksJson(blocksJson)
                be.copy(
                    contentBlocksJson = blocksJson,
                    contentNormalized = normalized,
                    searchContent = normalized
                )
            }

            dao.upsertBatchTransactional(enriched)
            try {
                dao.rebuildFts()
            } catch (_: Throwable) {
            }

            try {
                dao.getAll().size
            } catch (_: Throwable) {
            }

            batch.forEach { be ->
                val clean = TextSanitizer.sanitizeText(be.content)
                tempEntries.add(
                    KnowledgeEntry(
                        id = counters.idCounter.toString(),
                        title = be.title,
                        content = clean,
                        category = be.category,
                        source = be.source,
                        status = "parsed"
                    )
                )
                counters.idCounter++
                counters.totalImported++
            }

            emit(
                ImportProgress(
                    fileId = "",
                    fileName = fileName,
                    totalItems = null,
                    importedItems = counters.totalImported,
                    percent = 0,
                    status = "in_progress"
                )
            )
        } catch (e: Throwable) {
            emit(
                ImportProgress(
                    fileId = "",
                    fileName = fileName,
                    totalItems = null,
                    importedItems = counters.totalImported,
                    percent = 0,
                    status = "partial_failure",
                    message = e.message
                )
            )
        }
    }

    private fun takePersistableReadPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    private fun writeKnowledgeFile(gson: Gson, kf: KnowledgeFile, finalFile: File) {
        val dir = finalFile.parentFile ?: return
        val tmp = File.createTempFile("kbimp_", ".tmp", dir)
        OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8).use { it.write(gson.toJson(kf)) }
        tmp.copyTo(finalFile, overwrite = true)
        tmp.delete()
    }
}
