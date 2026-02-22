package com.example.powerai.data.importer

import android.util.Log
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.ArrayDeque

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

    // Small reusable mutable builder to reduce short-lived allocations.
    private class EntityBuilder {
        var id: Long = 0L
        var title: String = ""
        var content: String = ""
        var contentNormalized: String = ""
        var searchContent: String = ""
        var source: String = ""
        var contentBlocksJson: String? = null
        var pageNumber: Int? = null
        var bboxJson: String? = null
        var imageUris: String? = null
        var category: String = ""
        var keywordsSerialized: String = ""

        fun toEntity(): KnowledgeEntity {
            return KnowledgeEntity(
                id = id,
                title = title,
                content = content,
                contentNormalized = contentNormalized,
                searchContent = searchContent,
                source = source,
                contentBlocksJson = contentBlocksJson,
                pageNumber = pageNumber,
                bboxJson = bboxJson,
                imageUris = imageUris,
                category = category,
                keywordsSerialized = keywordsSerialized
            )
        }

        fun reset() {
            id = 0L
            title = ""
            content = ""
            contentNormalized = ""
            searchContent = ""
            source = ""
            contentBlocksJson = null
            pageNumber = null
            bboxJson = null
            imageUris = null
            category = ""
            keywordsSerialized = ""
        }
    }

    private val builderPool = ArrayDeque<EntityBuilder>()

    private fun obtainBuilder(): EntityBuilder {
        return if (builderPool.isEmpty()) EntityBuilder() else builderPool.removeFirst()
    }

    private fun releaseBuilder(b: EntityBuilder) {
        b.reset()
        if (builderPool.size < 128) builderPool.addFirst(b)
    }

    companion object {
        data class ParseBenchmark(val itemsParsed: Long, val maxMemoryBytes: Long, val durationMs: Long)

        /**
         * Dry-run parser for microbenchmarks: parses the stream but skips DAO writes.
         * Returns basic stats (items parsed, peak memory, duration).
         */
        @JvmStatic
        fun parseDry(inputStream: InputStream, sampleInterval: Int = 1000): ParseBenchmark {
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val jsonReader = JsonReader(reader)
            var parsed = 0L
            val runtime = Runtime.getRuntime()
            var maxUsed = 0L
            val start = System.nanoTime()
            try {
                val peek = jsonReader.peek()
                when (peek) {
                    JsonToken.BEGIN_ARRAY -> {
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            JsonParser().parse(jsonReader)
                            parsed++
                            if (parsed % sampleInterval == 0L) {
                                val used = runtime.totalMemory() - runtime.freeMemory()
                                if (used > maxUsed) maxUsed = used
                            }
                        }
                        jsonReader.endArray()
                    }
                    JsonToken.BEGIN_OBJECT -> {
                        val rootEl = JsonParser().parse(jsonReader)
                        if (rootEl.isJsonObject && rootEl.asJsonObject.has("entries")) {
                            val arr = rootEl.asJsonObject.getAsJsonArray("entries")
                            for (el in arr) {
                                parsed++
                                if (parsed % sampleInterval == 0L) {
                                    val used = runtime.totalMemory() - runtime.freeMemory()
                                    if (used > maxUsed) maxUsed = used
                                }
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported JSON top-level token: $peek")
                }
            } finally {
                try { jsonReader.close() } catch (_: Throwable) {}
            }
            val dur = (System.nanoTime() - start) / 1_000_000
            return ParseBenchmark(parsed, maxUsed, dur)
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
            val memory = object : KnowledgeDao {
                private val list = ArrayList<KnowledgeEntity>()

                override suspend fun insert(entity: KnowledgeEntity) {
                    list.add(entity)
                }

                override suspend fun insertBatch(entities: List<KnowledgeEntity>) {
                    list.addAll(entities)
                }

                override suspend fun upsertBatch(entities: List<KnowledgeEntity>) {
                    val byId = list.associateBy { it.id }.toMutableMap()
                    for (e in entities) byId[e.id] = e
                    list.clear()
                    list.addAll(byId.values)
                }

                override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
                    upsertBatch(entities)
                }

                override suspend fun getAll(): List<KnowledgeEntity> = list.toList()

                override suspend fun getById(id: Long): KnowledgeEntity? = list.find { it.id == id }

                override suspend fun updateBlocksJsonAndSearchFields(id: Long, contentBlocksJson: String, contentNormalized: String, searchContent: String) {
                    val idx = list.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        val e = list[idx]
                        list[idx] = e.copy(contentBlocksJson = contentBlocksJson, contentNormalized = contentNormalized, searchContent = searchContent)
                    }
                }

                override suspend fun getEntriesMissingNormalized(): List<KnowledgeEntity> = list.filter { it.contentNormalized.isEmpty() }

                override suspend fun countEntriesMissingNormalized(): Int = getEntriesMissingNormalized().size

                override suspend fun updateNormalizedContent(id: Long, normalized: String) {
                    val idx = list.indexOfFirst { it.id == id }
                    if (idx >= 0) list[idx] = list[idx].copy(contentNormalized = normalized, searchContent = normalized)
                }

                override suspend fun searchByKeyword(keyword: String): List<KnowledgeEntity> = list.filter { it.content.contains(keyword) || it.title.contains(keyword) }

                override suspend fun update(entity: KnowledgeEntity) {
                    val idx = list.indexOfFirst { it.id == entity.id }
                    if (idx >= 0) list[idx] = entity else list.add(entity)
                }

                override suspend fun searchByFts(query: String): List<KnowledgeEntity> = list.filter { it.contentNormalized.contains(query) }

                override suspend fun rebuildFts() {
                    // no-op for memory DAO
                }

                override suspend fun countFts(): Int = list.size

                override suspend fun getSample(n: Int): List<KnowledgeEntity> = list.take(n)

                override suspend fun countBySourcePrefix(sourcePrefix: String): Int = list.count { it.source.startsWith(sourcePrefix) }

                override suspend fun countMatchesBySourcePrefix(sourcePrefix: String, keywordNoSpace: String): Int = list.count { it.source.startsWith(sourcePrefix) && it.searchContent.replace(" ", "").contains(keywordNoSpace) }

                override suspend fun sampleBySourcePrefix(sourcePrefix: String, limit: Int): List<KnowledgeEntity> = list.filter { it.source.startsWith(sourcePrefix) }.take(limit)

                override suspend fun searchByKeywordNoSpace(keywordNoSpace: String): List<KnowledgeEntity> = list.filter { it.searchContent.replace(" ", "").contains(keywordNoSpace) }

                override suspend fun searchByKeywordFuzzy(pattern: String): List<KnowledgeEntity> = list.filter { it.searchContent.contains(pattern) }

                override suspend fun insertImportedFile(file: com.example.powerai.data.local.entity.ImportedFileEntity) {
                    // no-op
                }

                override suspend fun importedFileExists(fileId: String): Int = 0

                override suspend fun getImportedFiles(): List<com.example.powerai.data.local.entity.ImportedFileEntity> = emptyList()
            }

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

    private fun stableId64(input: String): Long {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val bb = ByteBuffer.wrap(digest)
            var v = bb.long and Long.MAX_VALUE
            if (v == 0L) v = 1L
            v
        } catch (_: Throwable) {
            val v = (input.hashCode().toLong() and Long.MAX_VALUE)
            if (v == 0L) 1L else v
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
        // use builder-based batch to avoid per-element KnowledgeEntity allocations
        val batchBuilders = ArrayList<EntityBuilder>(batchSize.coerceAtLeast(16))
        // Reuse a preallocated array for KnowledgeEntity and a lightweight AbstractList
        // wrapper that reads from the array without copying. This avoids per-batch
        // ArrayList allocations and reduces GC pressure.
        val capacity = batchSize.coerceAtLeast(16)
        val reusableEntitiesArray = arrayOfNulls<KnowledgeEntity>(capacity)
        var reusableEntitiesSize = 0
        val reusableEntitiesList = object : AbstractList<KnowledgeEntity>() {
            override fun get(index: Int): KnowledgeEntity = reusableEntitiesArray[index]!!
            override val size: Int
                get() = reusableEntitiesSize
        }
        var importedSoFar = 0L
        try {
            try { trace?.invoke("StreamingJsonResourceImporter: started") } catch (_: Throwable) {}

            val peek = jsonReader.peek()
            when (peek) {
                JsonToken.BEGIN_ARRAY -> {
                    jsonReader.beginArray()
                    val seenIds = HashSet<Long>()
                    while (jsonReader.hasNext()) {
                        try {
                            val el: JsonElement = JsonParser().parse(jsonReader)
                            val obj = if (el.isJsonObject) el.asJsonObject else null
                            if (obj == null) continue

                            val entryId = if (obj.has("entryId") && !obj.get("entryId").isJsonNull) obj.get("entryId").asString else null
                            val unitName = if (obj.has("unitName") && !obj.get("unitName").isJsonNull) obj.get("unitName").asString else null
                            val jobTitle = if (obj.has("jobTitle") && !obj.get("jobTitle").isJsonNull) obj.get("jobTitle").asString else null
                            val contentMarkdown = if (obj.has("contentMarkdown") && !obj.get("contentMarkdown").isJsonNull) obj.get("contentMarkdown").asString.orEmpty() else ""
                            val contentNormalized = if (obj.has("contentNormalized") && !obj.get("contentNormalized").isJsonNull) obj.get("contentNormalized").asString else null
                            val blocksElem = if (obj.has("blocks") && !obj.get("blocks").isJsonNull) obj.get("blocks") else null
                            val position = if (obj.has("position") && !obj.get("position").isJsonNull) try { obj.get("position").asInt } catch (_: Throwable) { 0 } else 0

                            val normalizedMarkdown = MarkdownTableNormalizer.normalizeMarkdownTables(contentMarkdown)
                            val blocksJson = try { blocksElem?.toString()?.takeIf { it.isNotBlank() && it != "null" } } catch (_: Throwable) { null }
                            val normalizedSourceText = if (!blocksJson.isNullOrBlank()) {
                                BlocksTextExtractor.extractPlainText(blocksJson)
                            } else {
                                contentNormalized ?: contentMarkdown
                            }

                            val isSplitEntry = (entryId?.contains("__p3_split") == true) || (jobTitle?.contains("（图") == true)

                            val sourceForEntity = fallbackFileId?.takeIf { it.isNotBlank() } ?: fallbackFileName?.takeIf { it.isNotBlank() } ?: "assets"

                            val normalized = if (isSplitEntry) "" else TextSanitizer.normalizeForSearch(normalizedSourceText)

                            val stablePkSeed = buildString {
                                append(sourceForEntity)
                                append("::")
                                append(entryId.orEmpty())
                                append("::")
                                append(jobTitle.orEmpty())
                                append("::")
                                append(position)
                            }
                            val stableId = stableId64(stablePkSeed)

                            if (seenIds.contains(stableId)) {
                                try { trace?.invoke("StreamingJsonResourceImporter: skipping duplicate id=$stableId") } catch (_: Throwable) {}
                                continue
                            }

                            val keywords = if (obj.has("tags") && !obj.get("tags").isJsonNull) {
                                try {
                                    val arr = obj.getAsJsonArray("tags")
                                    arr.mapNotNull { it?.asString?.trim() }.filter { it.isNotEmpty() }.joinToString(",")
                                } catch (_: Throwable) { "" }
                            } else {
                                ""
                            }

                            val builder = obtainBuilder()
                            builder.id = stableId
                            builder.title = jobTitle?.trim() ?: unitName?.trim() ?: (fallbackFileName ?: "无标题")
                            builder.content = normalizedMarkdown
                            builder.contentNormalized = normalized
                            builder.searchContent = normalized
                            builder.source = sourceForEntity
                            builder.contentBlocksJson = blocksJson
                            builder.pageNumber = if (position > 0) position else null
                            builder.bboxJson = null
                            builder.imageUris = null
                            builder.category = unitName ?: "未分类"
                            builder.keywordsSerialized = keywords

                            batchBuilders.add(builder)
                            seenIds.add(stableId)
                        } catch (elemEx: Throwable) {
                            try { trace?.invoke("StreamingJsonResourceImporter: element failed: ${elemEx.message}") } catch (_: Throwable) {}
                            // continue with next element
                        }

                        if (batchBuilders.size >= batchSize) {
                            if (reusableEntitiesArray.size < batchBuilders.size) {
                                throw IllegalStateException("reusableEntitiesArray too small")
                            }
                            var i = 0
                            for (b in batchBuilders) {
                                reusableEntitiesArray[i++] = b.toEntity()
                            }
                            reusableEntitiesSize = batchBuilders.size
                            dao.upsertBatchTransactional(reusableEntitiesList)
                            importedSoFar += reusableEntitiesSize
                            try { trace?.invoke("StreamingJsonResourceImporter: wrote batch size=$reusableEntitiesSize") } catch (_: Throwable) {}
                            emit(ImportProgress(fileId = fallbackFileId.orEmpty(), fileName = fallbackFileName.orEmpty(), totalItems = null, importedItems = importedSoFar, percent = 0, status = "in_progress"))
                            for (j in 0 until reusableEntitiesSize) reusableEntitiesArray[j] = null
                            for (b in batchBuilders) releaseBuilder(b)
                            batchBuilders.clear()
                        }
                    }
                    jsonReader.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    // materialize root object and check for entries property
                    val rootEl = JsonParser().parse(jsonReader)
                    if (rootEl.isJsonObject && rootEl.asJsonObject.has("entries")) {
                        val arr = rootEl.asJsonObject.getAsJsonArray("entries")
                        for (el in arr) {
                            val obj = if (el.isJsonObject) el.asJsonObject else continue
                            val entryId = if (obj.has("entryId") && !obj.get("entryId").isJsonNull) obj.get("entryId").asString else null
                            val unitName = if (obj.has("unitName") && !obj.get("unitName").isJsonNull) obj.get("unitName").asString else null
                            val jobTitle = if (obj.has("jobTitle") && !obj.get("jobTitle").isJsonNull) obj.get("jobTitle").asString else null
                            val contentMarkdown = if (obj.has("contentMarkdown") && !obj.get("contentMarkdown").isJsonNull) obj.get("contentMarkdown").asString else ""
                            val contentNormalized = if (obj.has("contentNormalized") && !obj.get("contentNormalized").isJsonNull) obj.get("contentNormalized").asString else null
                            val blocksElem = if (obj.has("blocks") && !obj.get("blocks").isJsonNull) obj.get("blocks") else null
                            val position = if (obj.has("position") && !obj.get("position").isJsonNull) try { obj.get("position").asInt } catch (_: Throwable) { 0 } else 0

                            val normalizedMarkdown = MarkdownTableNormalizer.normalizeMarkdownTables(contentMarkdown)
                            val blocksJson = try { blocksElem?.toString()?.takeIf { it.isNotBlank() && it != "null" } } catch (_: Throwable) { null }
                            val normalizedSourceText = if (!blocksJson.isNullOrBlank()) {
                                BlocksTextExtractor.extractPlainText(blocksJson)
                            } else {
                                contentNormalized ?: contentMarkdown
                            }

                            val isSplitEntry = (entryId?.contains("__p3_split") == true) || (jobTitle?.contains("（图") == true)
                            val sourceForEntity = fallbackFileId?.takeIf { it.isNotBlank() } ?: fallbackFileName?.takeIf { it.isNotBlank() } ?: "assets"
                            val normalized = if (isSplitEntry) "" else TextSanitizer.normalizeForSearch(normalizedSourceText)

                            val stablePkSeed = buildString {
                                append(sourceForEntity)
                                append("::")
                                append(entryId.orEmpty())
                                append("::")
                                append(jobTitle.orEmpty())
                                append("::")
                                append(position)
                            }
                            val stableId = stableId64(stablePkSeed)

                            val builder = obtainBuilder()
                            builder.id = stableId
                            builder.title = jobTitle?.trim() ?: unitName?.trim() ?: fallbackFileName.orEmpty().ifBlank { "无标题" }
                            builder.content = normalizedMarkdown
                            builder.contentNormalized = normalized
                            builder.searchContent = normalized
                            builder.source = sourceForEntity
                            builder.contentBlocksJson = blocksJson
                            builder.pageNumber = if (position > 0) position else null
                            builder.bboxJson = null
                            builder.imageUris = null
                            builder.category = unitName.orEmpty().ifBlank { "未分类" }
                            builder.keywordsSerialized = ""

                            batchBuilders.add(builder)
                            if (batchBuilders.size >= batchSize) {
                                if (reusableEntitiesArray.size < batchBuilders.size) throw IllegalStateException("reusableEntitiesArray too small")
                                var i2 = 0
                                for (b in batchBuilders) {
                                    reusableEntitiesArray[i2++] = b.toEntity()
                                }
                                reusableEntitiesSize = batchBuilders.size
                                dao.upsertBatchTransactional(reusableEntitiesList)
                                importedSoFar += reusableEntitiesSize
                                try { trace?.invoke("StreamingJsonResourceImporter: wrote batch size=$reusableEntitiesSize") } catch (_: Throwable) {}
                                emit(ImportProgress(fileId = fallbackFileId.orEmpty(), fileName = fallbackFileName.orEmpty(), totalItems = null, importedItems = importedSoFar, percent = 0, status = "in_progress"))
                                for (j in 0 until reusableEntitiesSize) reusableEntitiesArray[j] = null
                                for (b in batchBuilders) releaseBuilder(b)
                                batchBuilders.clear()
                            }
                        }
                    }
                }
                else -> {
                    // unsupported top-level
                    throw IllegalArgumentException("Unsupported JSON top-level token: $peek")
                }
            }

            if (batchBuilders.isNotEmpty()) {
                if (reusableEntitiesArray.size < batchBuilders.size) throw IllegalStateException("reusableEntitiesArray too small")
                var k = 0
                for (b in batchBuilders) {
                    reusableEntitiesArray[k++] = b.toEntity()
                }
                reusableEntitiesSize = batchBuilders.size
                dao.upsertBatchTransactional(reusableEntitiesList)
                importedSoFar += reusableEntitiesSize
                try { trace?.invoke("StreamingJsonResourceImporter: wrote final batch size=$reusableEntitiesSize") } catch (_: Throwable) {}
                for (j in 0 until reusableEntitiesSize) reusableEntitiesArray[j] = null
                for (b in batchBuilders) releaseBuilder(b)
                batchBuilders.clear()
            }

            try { dao.rebuildFts() } catch (_: Throwable) {}

            emit(ImportProgress(fileId = fallbackFileId.orEmpty(), fileName = fallbackFileName.orEmpty(), totalItems = null, importedItems = importedSoFar, percent = 100, status = "imported"))
        } catch (e: Exception) {
            Log.e("StreamingJsonResourceImporter", "import failed", e)
            val msg = try { e.stackTraceToString().take(1000) } catch (_: Throwable) { e.message }
            try { trace?.invoke("StreamingJsonResourceImporter: failed: $msg") } catch (_: Throwable) {}
            emit(ImportProgress(fileId = fallbackFileId.orEmpty(), fileName = fallbackFileName.orEmpty(), totalItems = null, importedItems = importedSoFar, percent = 0, status = "failed", message = msg))
        } finally {
            try { jsonReader.close() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)
}
