package com.example.powerai.data.importer

import android.util.Log
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * 适配 Python 预处理 JSON 的导入器。
 *
 * 将符合“黄金标准”JSON（见 design doc）转换为 `KnowledgeEntity` 并分批写入
 * `KnowledgeDao.insertBatchTransactional`，同时通过 Flow 实时发出 `ImportProgress`。
 */
class JsonResourceImporter(
    private val dao: KnowledgeDao,
    private val gson: Gson = Gson()
) {

    private fun stableId64(input: String): Long {
        // Deterministic 64-bit id derived from SHA-256. Keep it positive and non-zero.
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val bb = ByteBuffer.wrap(digest)
            var v = bb.long and Long.MAX_VALUE
            if (v == 0L) v = 1L
            v
        } catch (_: Throwable) {
            // Fallback: JVM hashCode (still deterministic within process); avoid 0.
            val v = (input.hashCode().toLong() and Long.MAX_VALUE)
            if (v == 0L) 1L else v
        }
    }

    private data class JsonSchema(
        val fileMetadata: FileMetadata,
        val entries: List<JsonEntry>
    )

    private data class FileMetadata(
        val fileName: String,
        val fileId: String,
        val importTimestamp: Long? = null,
        val source: String? = null,
        val entriesCount: Int = 0
    )

    private data class JsonEntry(
        val entryId: String? = null,
        val unitName: String? = null,
        val jobTitle: String? = null,
        val contentMarkdown: String? = null,
        val contentNormalized: String? = null,
        // Optional structured blocks payload. Exact schema is defined by the preprocessing pipeline.
        val blocks: JsonElement? = null,
        val position: Int = 0,
        val source: String? = null
    )

    // Legacy schema support: assets may contain { "entries": [ ... ] } without fileMetadata.
    private data class LegacySchema(
        val fileName: String? = null,
        val fileId: String? = null,
        val entries: List<JsonEntry> = emptyList()
    )

    /**
     * 从输入流解析 JSON，并分批写入 DAO，同时通过 Flow 返回 `ImportProgress`。
     *
     * @param inputStream JSON 字节流（UTF-8）
     * @param batchSize 每批写入的条目数量（默认 100）
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

            // metadata_index.json is an index file (not importable knowledge entries).
            val lowerFallbackId = fallbackFileId?.lowercase()
            val lowerFallbackName = fallbackFileName?.lowercase()
            if (lowerFallbackId == "metadata_index" || lowerFallbackName == "metadata_index.json") {
                emit(
                    ImportProgress(
                        fileId = fallbackFileId.orEmpty().ifBlank { "metadata_index" },
                        fileName = fallbackFileName.orEmpty().ifBlank { "metadata_index.json" },
                        totalItems = null,
                        importedItems = 0,
                        percent = 100,
                        status = "skipped",
                        message = "metadata index file; skipped"
                    )
                )
                return@flow
            }

            // Screenshot/table extraction manifest under assets (not importable KB entries).
            if (lowerFallbackName == "manifest.json") {
                emit(
                    ImportProgress(
                        fileId = fallbackFileId.orEmpty().ifBlank { "manifest" },
                        fileName = fallbackFileName.orEmpty().ifBlank { "manifest.json" },
                        totalItems = null,
                        importedItems = 0,
                        percent = 100,
                        status = "skipped",
                        message = "asset manifest file; skipped"
                    )
                )
                return@flow
            }

            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val root: JsonElement = JsonParser().parse(reader)

            // If some pipeline accidentally ships a manifest-like object, skip it.
            if (root.isJsonObject) {
                val obj = root.asJsonObject
                val looksLikeManifest = obj.has("version") && obj.has("fileId") && obj.has("items") && obj.has("pagesDir")
                if (looksLikeManifest) {
                    emit(
                        ImportProgress(
                            fileId = fallbackFileId.orEmpty().ifBlank { obj.get("fileId")?.asString.orEmpty() },
                            fileName = fallbackFileName.orEmpty().ifBlank { "manifest.json" },
                            totalItems = null,
                            importedItems = 0,
                            percent = 100,
                            status = "skipped",
                            message = "manifest-like json; skipped"
                        )
                    )
                    return@flow
                }
            }

            // Parse supported shapes:
            // A) { fileMetadata: {...}, entries: [...] }
            // B) { entries: [...] }  (legacy)
            // C) [ ... ]             (legacy array)
            val parsed: Pair<FileMetadata, List<JsonEntry>> = when {
                root.isJsonObject && root.asJsonObject.has("fileMetadata") -> {
                    val data = gson.fromJson(root, JsonSchema::class.java)
                    data.fileMetadata to data.entries
                }
                root.isJsonObject && root.asJsonObject.has("entries") -> {
                    val legacy = gson.fromJson(root, LegacySchema::class.java)
                    val fileName = legacy.fileName?.takeIf { it.isNotBlank() } ?: fallbackFileName.orEmpty().ifBlank { "assets" }
                    val fileId = legacy.fileId?.takeIf { it.isNotBlank() } ?: fallbackFileId.orEmpty().ifBlank { fileName }
                    FileMetadata(fileName = fileName, fileId = fileId, entriesCount = legacy.entries.size) to legacy.entries
                }
                root.isJsonArray -> {
                    val arr = root.asJsonArray
                    val entries = parseEntriesArray(arr)
                    val fileName = fallbackFileName.orEmpty().ifBlank { "assets" }
                    val fileId = fallbackFileId.orEmpty().ifBlank { fileName }
                    FileMetadata(fileName = fileName, fileId = fileId, entriesCount = entries.size) to entries
                }
                else -> {
                    throw IllegalArgumentException("Unsupported JSON schema: root=${root.javaClass.simpleName}")
                }
            }

            val metadata = parsed.first
            val entries = parsed.second
            metaFileName = metadata.fileName
            metaFileId = metadata.fileId
            val total = if (metadata.entriesCount > 0) metadata.entriesCount.toLong() else entries.size.toLong()

            // 初始进度
            emit(
                ImportProgress(
                    fileId = metadata.fileId,
                    fileName = metadata.fileName,
                    totalItems = if (total > 0) total else null,
                    importedItems = 0,
                    percent = 0,
                    status = "in_progress"
                )
            )

            var importedSoFar = 0L

            if (entries.isNotEmpty()) {
                entries.chunked(batchSize).forEach { batch ->
                    // Debug: log each incoming entry's jobTitle
                    batch.forEach { e ->
                        try {
                            Log.d("ImportDebug", "正在插入条目: ${e.jobTitle}")
                        } catch (_: Throwable) {
                        }
                    }

                    val entities = batch.map { e ->
                        val contentMarkdown = e.contentMarkdown.orEmpty()
                        val normalizedMarkdown = MarkdownTableNormalizer.normalizeMarkdownTables(contentMarkdown)
                        val blocksJson = try {
                            e.blocks?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        } catch (_: Throwable) {
                            null
                        }
                        val normalizedSourceText = if (!blocksJson.isNullOrBlank()) {
                            BlocksTextExtractor.extractPlainText(blocksJson)
                        } else {
                            e.contentNormalized ?: contentMarkdown
                        }

                        // Slot-splitting entries are image carriers (e.g. "表格#1（图3）").
                        // They should NOT participate in full-text search, otherwise a single keyword may hit
                        // many split variants of the same table.
                        val isSplitEntry = (e.entryId?.contains("__p3_split") == true) ||
                            (e.jobTitle?.contains("（图") == true)

                        // Source must be stable across builds to support reimport cleanup.
                        val sourceForEntity = metadata.source?.takeIf { it.isNotBlank() }
                            ?: metadata.fileId.takeIf { it.isNotBlank() }
                            ?: metadata.fileName

                        // collect image URIs and bounding box info from blocksJson when available
                        var imageUrisJson: String? = null
                        var bboxJson: String? = null
                        if (!blocksJson.isNullOrBlank()) {
                            try {
                                val blocksRoot = JsonParser().parse(blocksJson)
                                val imgs = ArrayList<String>()
                                val bboxes = ArrayList<String>()

                                fun visit(el: com.google.gson.JsonElement?) {
                                    if (el == null || el.isJsonNull) return
                                    when {
                                        el.isJsonPrimitive -> return
                                        el.isJsonArray -> el.asJsonArray.forEach { visit(it) }
                                        el.isJsonObject -> {
                                            val obj = el.asJsonObject
                                            // collect common image keys
                                            listOf("src", "url", "imageSrc").forEach { k ->
                                                val v = obj.get(k)
                                                if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                                                    val s = v.asString
                                                    if (s.isNotBlank()) imgs.add(s)
                                                }
                                            }
                                            // bounding box
                                            val bb = obj.get("boundingBox")
                                            if (bb != null && !bb.isJsonNull) {
                                                bboxes.add(bb.toString())
                                            }

                                            for ((_, v) in obj.entrySet()) visit(v)
                                        }
                                    }
                                }

                                visit(blocksRoot)
                                if (imgs.isNotEmpty()) imageUrisJson = gson.toJson(imgs)
                                if (bboxes.isNotEmpty()) bboxJson = gson.toJson(bboxes)
                            } catch (_: Throwable) {
                            }
                        } else {
                            // fallback: extract image refs from markdown content
                            try {
                                val imgRe = Regex("!\\[[^]]*\\]\\((file:///android_asset/[^)]+)\\)")
                                val matches = imgRe.findAll(contentMarkdown)
                                val found = matches.map { it.groupValues[1] }.toList()
                                if (found.isNotEmpty()) imageUrisJson = gson.toJson(found)
                            } catch (_: Throwable) {
                            }
                        }

                        val normalized = if (isSplitEntry) {
                            ""
                        } else {
                            TextSanitizer.normalizeForSearch(normalizedSourceText)
                        }

                        val stablePkSeed = buildString {
                            append(metadata.fileId)
                            append("::")
                            append(e.entryId.orEmpty())
                            append("::")
                            append(e.jobTitle.orEmpty())
                            append("::")
                            append(e.position)
                        }
                        val stableId = stableId64(stablePkSeed)

                        KnowledgeEntity(
                            id = stableId,
                            // 使用 JSON 中的字段，jobTitle 为条目标题，unitName 为章节/分类
                            title = e.jobTitle?.trim() ?: e.unitName?.trim() ?: metadata.fileName,
                            content = normalizedMarkdown,
                            contentNormalized = normalized,
                            searchContent = normalized,
                            source = sourceForEntity,
                            contentBlocksJson = blocksJson,
                            pageNumber = if (e.position > 0) e.position else null,
                            bboxJson = bboxJson,
                            imageUris = imageUrisJson,
                            category = e.unitName.orEmpty().ifBlank { "未分类" },
                            keywordsSerialized = ""
                        )
                    }

                    // 写入数据库（事务）
                    dao.upsertBatchTransactional(entities)

                    try { trace?.invoke("JsonResourceImporter: wrote batch size=${entities.size} importedSoFar=${importedSoFar + entities.size}") } catch (_: Throwable) {}

                    importedSoFar += batch.size

                    val percent = if (total > 0) ((importedSoFar * 100) / total).toInt().coerceIn(0, 100) else 0

                    emit(
                        ImportProgress(
                            fileId = metadata.fileId,
                            fileName = metadata.fileName,
                            totalItems = if (total > 0) total else null,
                            importedItems = importedSoFar,
                            percent = percent,
                            status = "in_progress"
                        )
                    )
                }

                // Rebuild FTS once after all inserts (much faster than rebuilding each batch).
                try {
                    dao.rebuildFts()
                } catch (_: Throwable) {
                }
            }

            // 完成
            try { trace?.invoke("JsonResourceImporter: completed importedSoFar=$importedSoFar") } catch (_: Throwable) {}
            emit(
                ImportProgress(
                    fileId = metadata.fileId,
                    fileName = metadata.fileName,
                    totalItems = if (total > 0) total else null,
                    importedItems = importedSoFar,
                    percent = 100,
                    status = "imported"
                )
            )
        } catch (e: Exception) {
            Log.e("JsonResourceImporter", "importFromJson failed", e)
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

    private fun parseEntriesArray(arr: JsonArray): List<JsonEntry> {
        if (arr.size() == 0) return emptyList()
        return try {
            arr.mapNotNull { el ->
                try {
                    gson.fromJson(el, JsonEntry::class.java)
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
