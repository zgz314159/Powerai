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
import kotlinx.coroutines.CancellationException
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
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                }

                emit(importedProgress(fallbackFileName, fallbackFileId, importedSoFar, "imported"))
            } catch (c: CancellationException) {
                // Cancellation must propagate: swallowing it here would let the caller
                // record a successful import for an aborted run.
                throw c
            } catch (e: Exception) {
                // A failed import must reach the caller so it can roll the partial writes back and
                // retry instead of marking the file imported. Only a diagnostic trace is emitted.
                try {
                    trace?.invoke("StreamingJsonResourceImporter: failed: ${e.message}")
                } catch (_: Throwable) {
                }
                throw e
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
    ): ImportProgress =
        ImportProgress(
            fileId = fileId.orEmpty(),
            fileName = fileName.orEmpty(),
            totalItems = null,
            importedItems = imported,
            percent = if (status == "imported") 100 else 0,
            status = status,
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
            } catch (c: CancellationException) {
                throw c
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

    /**
     * Stream a top-level object root (`{"fileMetadata":…,"entries":[…]}` or the legacy
     * `{"entries":[…]}`) one field / one entry at a time, so entries are never materialised as
     * a whole. Unknown root fields are skipped; the KB-declared `fileMetadata.source` is applied
     * to every entry that has no source of its own.
     */
    private suspend fun FlowCollector<ImportProgress>.streamObject(
        jsonReader: JsonReader,
        batchWriter: StreamingJsonBatchWriter,
        fallbackFileName: String?,
        fallbackFileId: String?,
        trace: ((String) -> Unit)?,
    ): Long {
        jsonReader.beginObject()
        val context = StreamedEntryContext(HashSet(), batchWriter, fallbackFileName, fallbackFileId)
        val fallbackSourceValue = fallbackFileId?.takeIf { it.isNotBlank() } ?: fallbackFileName.orEmpty()
        var declaredSource: String? = null
        var metadataSeen = false
        var entriesSeenBeforeMetadata = false
        var imported = 0L
        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                "fileMetadata" -> {
                    declaredSource = readDeclaredSource(jsonReader)
                    metadataSeen = true
                    // Legacy order: entries were already written with the asset-path fallback;
                    // flush the pending batch, then re-point them now that the source is known.
                    val source = declaredSource
                    if (entriesSeenBeforeMetadata && !source.isNullOrBlank()) {
                        imported += context.batchWriter.flush()
                        repointSource(context.seenIds, source, fallbackSourceValue)
                    }
                }
                "entries" -> {
                    if (!metadataSeen) entriesSeenBeforeMetadata = true
                    val fallbackMeta =
                        JsonResourceParser.FileMetadata(
                            fileName = fallbackFileName.orEmpty(),
                            fileId = fallbackFileId.orEmpty(),
                            source = declaredSource,
                        )
                    imported = streamEntries(jsonReader, fallbackMeta, imported, context, trace)
                }
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()
        return imported
    }

    /** Read entries one element at a time from the enclosing array, flushing batches as usual. */
    private suspend fun FlowCollector<ImportProgress>.streamEntries(
        jsonReader: JsonReader,
        fallbackMeta: JsonResourceParser.FileMetadata,
        importedSoFar: Long,
        context: StreamedEntryContext,
        trace: ((String) -> Unit)?,
    ): Long {
        if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
            jsonReader.skipValue()
            return importedSoFar
        }
        jsonReader.beginArray()
        var imported = importedSoFar
        while (jsonReader.hasNext()) {
            try {
                val el: JsonElement = JsonParser().parse(jsonReader)
                val obj = if (el.isJsonObject) el.asJsonObject else null
                if (obj != null) imported = writeEntry(this, obj, fallbackMeta, imported, context)
            } catch (c: CancellationException) {
                throw c
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

    /** Read the KB-declared `source` from a `fileMetadata` object, skipping its other fields. */
    private fun readDeclaredSource(jsonReader: JsonReader): String? {
        if (jsonReader.peek() != JsonToken.BEGIN_OBJECT) {
            jsonReader.skipValue()
            return null
        }
        var source: String? = null
        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            if (jsonReader.nextName() == "source" && jsonReader.peek() == JsonToken.STRING) {
                source = jsonReader.nextString()
            } else {
                jsonReader.skipValue()
            }
        }
        jsonReader.endObject()
        return source?.takeIf { it.isNotBlank() }
    }

    /**
     * Bounded compatibility for legacy roots that list `entries` before `fileMetadata`: the
     * declared source is only known after the rows were written, so re-point the rows that fell
     * back to the asset-path source. Rows with their own entry-level source are left untouched.
     */
    private suspend fun repointSource(
        ids: Set<Long>,
        declaredSource: String,
        fallbackSourceValue: String,
    ) {
        for (id in ids) {
            val entity = dao.getById(id) ?: continue
            if (entity.source != fallbackSourceValue) continue
            dao.update(entity.copy(source = declaredSource))
        }
    }

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
