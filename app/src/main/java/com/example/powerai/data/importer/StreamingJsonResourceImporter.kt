package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao

// TODO: 本组件负责流JSON 导入。解析逻辑已抽取到
// [StreamingJsonEntryParser]，批量写入已重构[StreamingJsonBatchWriter]
// 将来如有需要可以进一步抽离错误回溯或进度计算逻辑

import com.example.powerai.core.data.entity.KnowledgeEntity
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
import com.example.powerai.data.importer.ImportUtils
import com.example.powerai.data.importer.StreamingJsonBatchWriter

/**
 * Incremental streaming importer: processes JSON elements one-by-one and writes
 * to DB in batches to reduce peak memory usage. This first iteration parses
 * element-by-element (still using Gson for per-element mapping) and reuses a
 * mutable batch container.
 */


class StreamingJsonResourceImporter(
    private val dao: KnowledgeDao,
    private val gson: Gson = Gson()
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
        fun parseDry(inputStream: InputStream, sampleInterval: Int = 1000): ParseBenchmark {
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
            fallbackFileId: String? = null
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
        fallbackFileId: String? = null
    ): Flow<ImportProgress> = flow {
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val jsonReader = JsonReader(reader)
        // delegate batching to helper class
        val batchWriter = StreamingJsonBatchWriter(dao, batchSize, trace)
        var importedSoFar = 0L
        try {
            try { trace?.invoke("StreamingJsonResourceImporter: started") } catch (_: Throwable) {}

            val peek = jsonReader.peek()
            when (peek) {
                JsonToken.BEGIN_ARRAY -> {
                    jsonReader.beginArray()
                    val seenIds = HashSet<Long>()
                    val arrayFallbackMeta = JsonResourceParser.FileMetadata(
                        fileName = fallbackFileName.orEmpty(),
                        fileId = fallbackFileId.orEmpty()
                    )
                    val context = StreamedEntryContext(seenIds, batchWriter, fallbackFileName, fallbackFileId)
                    while (jsonReader.hasNext()) {
                        try {
                            val el: JsonElement = JsonParser().parse(jsonReader)
                            val obj = (if (el.isJsonObject) el.asJsonObject else null) ?: continue
                            importedSoFar = writeEntry(this, obj, arrayFallbackMeta, importedSoFar, context)
                        } catch (elemEx: Throwable) {
                            try { trace?.invoke("StreamingJsonResourceImporter: element failed: ${elemEx.message}") } catch (_: Throwable) {}
                        }
                    }
                    jsonReader.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    // materialize root object and check for entries property
                    val rootEl = JsonParser().parse(jsonReader)
                    if (rootEl.isJsonObject && rootEl.asJsonObject.has("entries")) {
                        val arr = rootEl.asJsonObject.getAsJsonArray("entries")
                        // KB-declared source (e.g. "pdf:{sha256}::{name}") must survive import.
                        val declaredSource = rootEl.asJsonObject
                            .get("fileMetadata")
                            ?.takeIf { it.isJsonObject }
                            ?.asJsonObject
                            ?.get("source")
                            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                            ?.asString
                        val objectFallbackMeta = JsonResourceParser.FileMetadata(
                            fileName = fallbackFileName.orEmpty(),
                            fileId = fallbackFileId.orEmpty(),
                            source = declaredSource
                        )
                        val seenIds = HashSet<Long>()
                        val context = StreamedEntryContext(seenIds, batchWriter, fallbackFileName, fallbackFileId)
                        for (el in arr) {
                            val obj = if (el.isJsonObject) el.asJsonObject else continue
                            try {
                                importedSoFar = writeEntry(this, obj, objectFallbackMeta, importedSoFar, context)
                            } catch (elemEx: Throwable) {
                                try { trace?.invoke("StreamingJsonResourceImporter: element failed: ${'$'}{elemEx.message}") } catch (_: Throwable) {}
                                continue
                            }

                        }
                    }
                }
                else -> {
                    // unsupported top-level
                    throw IllegalArgumentException("Unsupported JSON top-level token: $peek")
                }
            }

            // flush remaining builders
            val written = batchWriter.flush()
            importedSoFar += written
            try { trace?.invoke("StreamingJsonResourceImporter: wrote final batch size=$written") } catch (_: Throwable) {}

            try { dao.rebuildFts() } catch (_: Throwable) {}

            emit(ImportProgress(fileId = fallbackFileId.orEmpty(), fileName = fallbackFileName.orEmpty(), totalItems = null, importedItems = importedSoFar, percent = 100, status = "imported"))
        } catch (e: Exception) {
            // use provided trace callback for diagnostics instead of android.util.Log
            val msg = try { e.stackTraceToString().take(1000) } catch (_: Throwable) { e.message }
            try { trace?.invoke("StreamingJsonResourceImporter: failed: $msg") } catch (_: Throwable) {}
            emit(ImportProgress(fileId = fallbackFileId.orEmpty(), fileName = fallbackFileName.orEmpty(), totalItems = null, importedItems = importedSoFar, percent = 0, status = "failed", message = msg))
        } finally {
            try { jsonReader.close() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    /** Loop-invariant inputs for [writeEntry], grouped to keep the parameter list short. */
    private class StreamedEntryContext(
        val seenIds: MutableSet<Long>,
        val batchWriter: StreamingJsonBatchWriter,
        val fileName: String?,
        val fileId: String?
    )

    private suspend fun writeEntry(
        collector: FlowCollector<ImportProgress>,
        obj: JsonObject,
        fallbackMeta: JsonResourceParser.FileMetadata,
        importedSoFar: Long,
        context: StreamedEntryContext
    ): Long {
        val builder = obtainBuilder()
        if (!StreamingJsonEntryParser.fillBuilder(obj, builder, context.seenIds, fallbackMeta)) {
            releaseBuilder(builder)
            return importedSoFar
        }
        val total = importedSoFar + context.batchWriter.add(builder)
        collector.emit(
            ImportProgress(
                fileId = context.fileId.orEmpty(),
                fileName = context.fileName.orEmpty(),
                totalItems = null,
                importedItems = total,
                percent = 0,
                status = "in_progress"
            )
        )
        return total
    }
}
