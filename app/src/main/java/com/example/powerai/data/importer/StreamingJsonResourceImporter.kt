package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao

// TODO: 本组件负责流JSON 导入。解析逻辑已抽取到
// [StreamingJsonEntryParser]，批量写入已重构[StreamingJsonBatchWriter]
// 将来如有需要可以进一步抽离错误回溯或进度计算逻辑

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Incremental streaming importer: processes JSON elements one-by-one and writes
 * to DB in batches to reduce peak memory usage. This first iteration parses
 * element-by-element (still using Gson for per-element mapping) and reuses a
 * mutable batch container.
 */

class StreamingJsonResourceImporter(
    private val dao: KnowledgeDao,
    private val gson: Gson = Gson(),
) {
    private val builderPool = EntityBuilderPool()

    private fun obtainBuilder(): EntityBuilder = builderPool.obtain()

    private fun releaseBuilder(b: EntityBuilder) = builderPool.release(b)

    companion object {
        data class ParseBenchmark(val itemsParsed: Long, val maxMemoryBytes: Long, val durationMs: Long)

        /**
         * Dry-run parser for microbenchmarks: parses the stream but skips DAO writes.
         * Returns basic stats (items parsed, peak memory, duration).
         */
        @JvmStatic
        fun parseDry(
            inputStream: InputStream,
            sampleInterval: Int = 1000,
        ): ParseBenchmark {
            val result = StreamingJsonBenchmark.parseDry(inputStream, sampleInterval)
            return ParseBenchmark(result.itemsParsed, result.maxMemoryBytes, result.durationMs)
        }

        data class ImportResult(val importedItems: Long, val ftsCount: Int)

        @JvmStatic
        fun importIntoMemoryDaoBlocking(
            inputStream: InputStream,
            batchSize: Int = ImportDefaults.DEFAULT_BATCH_SIZE,
            trace: ((String) -> Unit)? = null,
            fallbackFileName: String? = null,
            fallbackFileId: String? = null,
        ): ImportResult {
            val memory = MemoryKnowledgeDao()

            var importedSoFar = 0L
            kotlinx.coroutines.runBlocking {
                val importer = StreamingJsonResourceImporter(memory)
                importer.importFromJson(inputStream, batchSize, trace, fallbackFileName, fallbackFileId).collect { p ->
                    importedSoFar = p.importedItems
                }
            }

            val fts = kotlinx.coroutines.runBlocking { memory.countFts() }
            return ImportResult(importedSoFar, fts)
        }
    }

    fun importFromJson(
        inputStream: InputStream,
        batchSize: Int = ImportDefaults.DEFAULT_BATCH_SIZE,
        trace: ((String) -> Unit)? = null,
        fallbackFileName: String? = null,
        fallbackFileId: String? = null,
    ): Flow<ImportProgress> =
        flow {
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val jsonReader = JsonReader(reader)
            val batchWriter = StreamingJsonBatchWriter(dao, batchSize, trace)
            var importedSoFar = 0L
            try {
                try {
                    trace?.invoke("StreamingJsonResourceImporter: started")
                } catch (_: Throwable) {
                }

                when (val peek = jsonReader.peek()) {
                    JsonToken.BEGIN_ARRAY ->
                        importedSoFar +=
                            streamArray(jsonReader, batchWriter, fallbackFileName, fallbackFileId, trace)
                    JsonToken.BEGIN_OBJECT ->
                        importedSoFar +=
                            streamObject(jsonReader, batchWriter, fallbackFileName, fallbackFileId, trace)
                    else -> throw IllegalArgumentException("Unsupported JSON top-level token: $peek")
                }

                // flush remaining builders
                val written = batchWriter.flush()
                importedSoFar += written
                try {
                    trace?.invoke("StreamingJsonResourceImporter: wrote final batch size=$written")
                } catch (_: Throwable) {
                }

                try {
                    dao.rebuildFts()
                } catch (_: Throwable) {
                }

                emit(importedProgress(fallbackFileName, fallbackFileId, importedSoFar, "imported"))
            } catch (e: Exception) {
                // use provided trace callback for diagnostics instead of android.util.Log
                val msg =
                    try {
                        e.stackTraceToString().take(1000)
                    } catch (_: Throwable) {
                        e.message
                    }
                try {
                    trace?.invoke("StreamingJsonResourceImporter: failed: $msg")
                } catch (_: Throwable) {
                }
                emit(importedProgress(fallbackFileName, fallbackFileId, importedSoFar, "failed", msg))
            } finally {
                try {
                    jsonReader.close()
                } catch (_: Throwable) {
                }
            }
        }.flowOn(Dispatchers.IO)

    private fun importedProgress(
        fileName: String?,
        fileId: String?,
        imported: Long,
        status: String,
        message: String? = null,
    ): ImportProgress =
        ImportProgress(
            fileId = fileId.orEmpty(),
            fileName = fileName.orEmpty(),
            totalItems = null,
            importedItems = imported,
            percent = if (status == "imported") 100 else 0,
            status = status,
            message = message,
        )

    /** Stream a top-level JSON array of entries, returning the number of rows written. */
    private suspend fun FlowCollector<ImportProgress>.streamArray(
        jsonReader: JsonReader,
        batchWriter: StreamingJsonBatchWriter,
        fallbackFileName: String?,
        fallbackFileId: String?,
        trace: ((String) -> Unit)?,
    ): Long {
        jsonReader.beginArray()
        val fallbackMeta =
            JsonResourceParser.FileMetadata(
                fileName = fallbackFileName.orEmpty(),
                fileId = fallbackFileId.orEmpty(),
            )
        val context = StreamedEntryContext(HashSet(), batchWriter, fallbackFileName, fallbackFileId)
        var imported = 0L
        while (jsonReader.hasNext()) {
            try {
                val el: JsonElement = JsonParser().parse(jsonReader)
                val obj = (if (el.isJsonObject) el.asJsonObject else null) ?: continue
                imported = writeEntry(this, obj, fallbackMeta, imported, context)
            } catch (elemEx: Throwable) {
                try {
                    trace?.invoke("StreamingJsonResourceImporter: element failed: ${elemEx.message}")
                } catch (_: Throwable) {
                }
            }
        }
        jsonReader.endArray()
        return imported
    }

    /** Stream a top-level `{ "entries": [...] }` object, returning the number of rows written. */
    private suspend fun FlowCollector<ImportProgress>.streamObject(
        jsonReader: JsonReader,
        batchWriter: StreamingJsonBatchWriter,
        fallbackFileName: String?,
        fallbackFileId: String?,
        trace: ((String) -> Unit)?,
    ): Long {
        val rootEl = JsonParser().parse(jsonReader)
        if (!rootEl.isJsonObject || !rootEl.asJsonObject.has("entries")) return 0L
        val arr = rootEl.asJsonObject.getAsJsonArray("entries")
        // KB-declared source (e.g. "pdf:{sha256}::{name}") must survive import.
        val fallbackMeta =
            JsonResourceParser.FileMetadata(
                fileName = fallbackFileName.orEmpty(),
                fileId = fallbackFileId.orEmpty(),
                source = declaredSourceOf(rootEl.asJsonObject),
            )
        val context = StreamedEntryContext(HashSet(), batchWriter, fallbackFileName, fallbackFileId)
        var imported = 0L
        for (el in arr) {
            val obj = if (el.isJsonObject) el.asJsonObject else continue
            try {
                imported = writeEntry(this, obj, fallbackMeta, imported, context)
            } catch (elemEx: Throwable) {
                try {
                    trace?.invoke("StreamingJsonResourceImporter: element failed: ${'$'}{elemEx.message}")
                } catch (_: Throwable) {
                }
                continue
            }
        }
        return imported
    }

    private fun declaredSourceOf(root: JsonObject): String? =
        root
            .get("fileMetadata")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("source")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString

    /** Loop-invariant inputs for [writeEntry], grouped to keep the parameter list short. */
    private class StreamedEntryContext(
        val seenIds: MutableSet<Long>,
        val batchWriter: StreamingJsonBatchWriter,
        val fileName: String?,
        val fileId: String?,
    )

    private suspend fun writeEntry(
        collector: FlowCollector<ImportProgress>,
        obj: JsonObject,
        fallbackMeta: JsonResourceParser.FileMetadata,
        importedSoFar: Long,
        context: StreamedEntryContext,
    ): Long {
        val builder = obtainBuilder()
        if (!StreamingJsonEntryParser.fillBuilder(obj, builder, context.seenIds, fallbackMeta)) {
            releaseBuilder(builder)
            return importedSoFar
        }
        val total = importedSoFar + context.batchWriter.add(builder)
        collector.emit(importedProgress(context.fileName, context.fileId, total, "in_progress"))
        return total
    }
}
