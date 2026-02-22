package com.example.powerai.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.powerai.data.importer.BlocksPreprocessor
import com.example.powerai.data.importer.DocxParser
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.importer.PdfParser
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.importer.TxtParser
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.KnowledgeEntity
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

    fun importUriFlow(
        context: Context,
        dao: KnowledgeDao,
        pdfParser: PdfParser,
        gson: Gson,
        storageDir: () -> File,
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
        batchSize: Int
    ): Flow<ImportProgress> = channelFlow {
        Log.e("FLOW_TRIGGER", "Flow object created for $displayName")
        Log.e("FLOW_STATUS", "!!! Repository 收到导入请求 !!!")

        withContext(Dispatchers.IO) {
            try {
                Log.d("KnowledgeRepository", "importUriFlow start: displayName=$displayName uri=$uri batchSize=$batchSize")
                val lower = displayName.lowercase()
                var idCounter = 1L
                val tempEntries = mutableListOf<KnowledgeEntry>()
                var totalImported = 0L

                val onBatch: suspend (List<KnowledgeEntity>) -> Unit = { batch ->
                    try {
                        // Persist parsed KnowledgeEntity batch into Room.
                        // For non-JSON imports, also write structured blocks so the rest of the app
                        // reads only preprocessed DB fields.
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
                            Log.d("KnowledgeRepository", "onBatch: rebuilt FTS after persisting batch")
                        } catch (t: Throwable) {
                            Log.w("KnowledgeRepository", "onBatch: rebuildFts failed", t)
                        }

                        // Log DB size after insertion for debugging
                        try {
                            val total = dao.getAll().size
                            Log.d("KnowledgeRepository", "onBatch persisted ${batch.size} items, dbTotal=$total")
                        } catch (_: Throwable) {
                        }

                        Log.d("KnowledgeRepository", "onBatch persisted ${batch.size} items")

                        // Also accumulate entries for JSON export/return
                        batch.forEach { be ->
                            val clean = TextSanitizer.sanitizeText(be.content)
                            val entry = KnowledgeEntry(
                                id = idCounter.toString(),
                                title = be.title,
                                content = clean,
                                category = be.category,
                                source = be.source,
                                status = "parsed"
                            )
                            tempEntries.add(entry)
                            idCounter++
                            totalImported++
                        }
                        Log.d("KnowledgeRepository", "sending in_progress imported=$totalImported")
                        send(
                            ImportProgress(
                                fileId = "",
                                fileName = displayName,
                                totalItems = null,
                                importedItems = totalImported,
                                percent = 0,
                                status = "in_progress"
                            )
                        )
                    } catch (e: Throwable) {
                        // Log and continue; do not fail the entire import for a single batch
                        Log.e("KnowledgeRepository", "onBatch failed", e)
                        send(
                            ImportProgress(
                                fileId = "",
                                fileName = displayName,
                                totalItems = null,
                                importedItems = totalImported,
                                percent = 0,
                                status = "partial_failure",
                                message = e.message
                            )
                        )
                    }
                }

                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Log.d("KnowledgeRepository", "Persisted read permission for $uri")
                } catch (e: Exception) {
                    Log.w("KnowledgeRepository", "Failed to takePersistableUriPermission for $uri", e)
                }

                Log.d("KnowledgeRepository", "Starting parsing for displayName=$displayName")
                val mimeType = try {
                    context.contentResolver.getType(uri)
                } catch (_: Exception) {
                    null
                }
                Log.d("KnowledgeRepository", "parsing: displayName=$displayName lower=$lower mimeType=$mimeType uri=$uri")
                val fileId = when {
                    mimeType == "text/plain" || lower.endsWith(".txt") -> {
                        Log.d("KnowledgeRepository", "using TxtParser")
                        TxtParser(contentResolver).parse(uri, displayName, batchSize, onBatch)
                    }

                    mimeType == "application/pdf" || lower.endsWith(".pdf") -> {
                        Log.d("KnowledgeRepository", "using PdfParser")
                        pdfParser.parse(uri, displayName, batchSize, onBatch)
                    }

                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        mimeType == "application/msword" || lower.endsWith(".docx") || lower.endsWith(".doc") -> {
                        Log.d("KnowledgeRepository", "using DocxParser")
                        DocxParser(contentResolver).parse(uri, displayName, batchSize, onBatch)
                    }

                    else -> {
                        Log.w("KnowledgeRepository", "Unsupported file type: displayName=$displayName lower=$lower mimeType=$mimeType")
                        throw IllegalArgumentException("Unsupported file type")
                    }
                }

                Log.d("KnowledgeRepository", "Parsing finished, fileId=$fileId")

                val dir = storageDir()
                val finalFile = File(dir, "$fileId.json")
                if (finalFile.exists()) {
                    Log.d("KnowledgeRepository", "File already imported: $fileId")
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
                val tmp = File.createTempFile("kbimp_", ".tmp", dir)
                OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8).use { it.write(gson.toJson(kf)) }
                tmp.copyTo(finalFile, overwrite = true)
                tmp.delete()

                Log.d("KnowledgeRepository", "Import completed for fileId=$fileId entries=${tempEntries.size}")
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
                Log.e("KnowledgeRepository", "importUriFlow failed", e)
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
}
