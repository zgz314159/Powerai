package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao

// TODO: 导入器逻辑已划分为解析 ([JsonResourceParser]) 和批次提交，
// 只保留高层流程。后续可针对元数错误分类进一步拆分

import com.example.powerai.util.PLog
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.example.powerai.data.importer.JsonResourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.InputStreamReader
import com.example.powerai.data.importer.ImportUtils

/**
 * 适配 Python 预处JSON 的导入器
 *
 * 将符合“黄金标准”JSON（见 design doc）转换为 `KnowledgeEntity` 并分批写
 * `KnowledgeDao.insertBatchTransactional`，同时通过 Flow 实时发出 `ImportProgress`
 */
class JsonResourceImporter(
    private val dao: KnowledgeDao,
    private val gson: Gson = Gson()
) {



    /**
     * 从输入流解析 JSON，并分批写入 DAO，同时通过 Flow 返回 `ImportProgress`
     *
     * @param inputStream JSON 字节流（UTF-8
     * @param batchSize 每批写入的条目数量（默认 100
     */
    fun importFromJson(
        inputStream: InputStream,
        batchSize: Int = ImportDefaults.DEFAULT_BATCH_SIZE,
        trace: ((String) -> Unit)? = null,
        fallbackFileName: String? = null,
        fallbackFileId: String? = null
    ): Flow<ImportProgress> = flow {
        var metaFileName: String? = null
        var metaFileId: String? = null
        try {
            try { trace?.invoke("JsonResourceImporter: parsing started") } catch (_: Throwable) {}

            // Check if file should be skipped (metadata_index, manifest, etc.)
            val skipResult = checkAndSkipNonImportableFiles(fallbackFileId, fallbackFileName)
            if (skipResult != null) {
                emit(skipResult)
                return@flow
            }

            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val root: JsonElement = JsonParser().parse(reader)

            // Check if JSON looks like manifest object (structural check)
            val manifestCheckResult = checkAndSkipManifestLikeJson(root, fallbackFileId, fallbackFileName)
            if (manifestCheckResult != null) {
                emit(manifestCheckResult)
                return@flow
            }

            // delegate the heavy lifting to shared parser
            val parsed = JsonResourceParser.parseRoot(root, gson, fallbackFileName, fallbackFileId)

            val metadata = parsed.first
            val entries = parsed.second
            metaFileName = metadata.fileName
            metaFileId = metadata.fileId
            val total = if (metadata.entriesCount > 0) metadata.entriesCount.toLong() else entries.size.toLong()

            // 初始进度
            emit(
                makeImportProgress(
                    fileId = metadata.fileId,
                    fileName = metadata.fileName,
                    totalItems = if (total > 0) total else null,
                    importedItems = 0,
                    total = total,
                    status = "in_progress"
                )
            )

            var importedSoFar = 0L
            val batchWriter = StreamingJsonBatchWriter(dao, batchSize, trace)

            if (entries.isNotEmpty()) {
                importedSoFar = processBatchAndEmitProgress(
                    entries, metadata, batchWriter, total
                ) { progress -> emit(progress) }

                // flush any remainder and rebuild
                importedSoFar += batchWriter.flush()
                try {
                    dao.rebuildFts()
                } catch (_: Throwable) {
                }
            }

            // 完成
            emitCompletionProgress(metadata, importedSoFar, trace)
            emit(
                makeImportProgress(
                    fileId = metadata.fileId,
                    fileName = metadata.fileName,
                    totalItems = if (total > 0) total else null,
                    importedItems = importedSoFar,
                    total = total,
                    status = "imported",
                    forcePercent = 100
                )
            )
        } catch (e: Exception) {
            PLog.e("JsonResourceImporter", "importFromJson failed", e)
            val msg = try { e.stackTraceToString().take(1000) } catch (_: Throwable) { e.message }
            try { trace?.invoke("JsonResourceImporter: failed: $msg") } catch (_: Throwable) {}
            emit(
                ImportProgress(
                    fileId = metaFileId.orEmpty(),
                    fileName = metaFileName.orEmpty(),
                    totalItems = null,
                    importedItems = 0,
                    percent = 0,
                    status = "failed",
                    message = msg
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun checkAndSkipNonImportableFiles(
        fallbackFileId: String?,
        fallbackFileName: String?
    ): ImportProgress? {
        val lowerFallbackId = fallbackFileId?.lowercase()
        val lowerFallbackName = fallbackFileName?.lowercase()
        
        // metadata_index.json is an index file (not importable knowledge entries).
        if (lowerFallbackId == "metadata_index" || lowerFallbackName == "metadata_index.json") {
            return ImportProgress(
                fileId = fallbackFileId.orEmpty().ifBlank { "metadata_index" },
                fileName = fallbackFileName.orEmpty().ifBlank { "metadata_index.json" },
                totalItems = null,
                importedItems = 0,
                percent = 100,
                status = "skipped",
                message = "metadata index file; skipped"
            )
        }

        // Screenshot/table extraction manifest under assets (not importable KB entries).
        if (lowerFallbackName == "manifest.json") {
            return ImportProgress(
                fileId = fallbackFileId.orEmpty().ifBlank { "manifest" },
                fileName = fallbackFileName.orEmpty().ifBlank { "manifest.json" },
                totalItems = null,
                importedItems = 0,
                percent = 100,
                status = "skipped",
                message = "asset manifest file; skipped"
            )
        }
        return null
    }

    private fun checkAndSkipManifestLikeJson(
        root: JsonElement,
        fallbackFileId: String?,
        fallbackFileName: String?
    ): ImportProgress? {
        // If some pipeline accidentally ships a manifest-like object, skip it.
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val looksLikeManifest = obj.has("version") && obj.has("fileId") && obj.has("items") && obj.has("pagesDir")
            if (looksLikeManifest) {
                return ImportProgress(
                    fileId = fallbackFileId.orEmpty().ifBlank { obj.get("fileId")?.asString.orEmpty() },
                    fileName = fallbackFileName.orEmpty().ifBlank { "manifest.json" },
                    totalItems = null,
                    importedItems = 0,
                    percent = 100,
                    status = "skipped",
                    message = "manifest-like json; skipped"
                )
            }
        }
        return null
    }

    private suspend fun processBatchAndEmitProgress(
        entries: List<JsonResourceParser.JsonEntry>,
        metadata: JsonResourceParser.FileMetadata,
        batchWriter: StreamingJsonBatchWriter,
        total: Long,
        emitProgress: suspend (ImportProgress) -> Unit
    ): Long {
        var importedSoFar = 0L
        for (e in entries) {
            try {
                PLog.d("ImportDebug", "正在插入条目: ${e.jobTitle}")
            } catch (_: Throwable) {
            }

            val entity = JsonEntryMapper.toEntity(e, metadata, gson)
            val written = batchWriter.addEntity(entity)
            importedSoFar += written
            emitProgress(
                makeImportProgress(
                    fileId = metadata.fileId,
                    fileName = metadata.fileName,
                    totalItems = if (total > 0) total else null,
                    importedItems = importedSoFar,
                    total = total,
                    status = "in_progress"
                )
            )
        }
        return importedSoFar
    }

    private fun emitCompletionProgress(
        metadata: JsonResourceParser.FileMetadata,
        importedSoFar: Long,
        trace: ((String) -> Unit)?
    ) {
        try { trace?.invoke("JsonResourceImporter: completed importedSoFar=$importedSoFar") } catch (_: Throwable) {}
    }

    private fun makeImportProgress(
        fileId: String,
        fileName: String,
        totalItems: Long?,
        importedItems: Long,
        total: Long,
        status: String,
        forcePercent: Int? = null
    ): ImportProgress {
        val percent = forcePercent ?: calculateImportPercent(importedItems, total)
        return ImportProgress(
            fileId = fileId,
            fileName = fileName,
            totalItems = totalItems,
            importedItems = importedItems,
            percent = percent,
            status = status
        )
    }

    private fun calculateImportPercent(imported: Long, total: Long): Int =
        if (total > 0) ((imported * 100) / total).toInt().coerceIn(0, 100) else 0
}
