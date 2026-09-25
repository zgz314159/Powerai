package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao

import com.example.powerai.core.model.ImportedFile

import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.ImportedFileEntity
import java.util.ArrayDeque

/**
 * Small reusable mutable builder to reduce short-lived allocations.
 */
internal class EntityBuilder {
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

/**
 * Simple object pool for reusable EntityBuilder instances to reduce allocation pressure.
 */
internal class EntityBuilderPool(initialCapacity: Int = 16) {
    private val pool = ArrayDeque<EntityBuilder>(initialCapacity)

    fun obtain(): EntityBuilder {
        return if (pool.isEmpty()) EntityBuilder() else pool.removeFirst()
    }

    fun release(builder: EntityBuilder) {
        builder.reset()
        if (pool.size < 128) pool.addFirst(builder)
    }
}

/**
 * Benchmark utilities for streaming JSON import performance analysis.
 */
internal object StreamingJsonBenchmark {
    data class BenchmarkResult(val itemsParsed: Long, val maxMemoryBytes: Long, val durationMs: Long)

    /**
     * Dry-run parser for microbenchmarks: parses the stream but skips DAO writes.
     * Returns basic stats (items parsed, peak memory, duration).
     */
    @JvmStatic
    fun parseDry(inputStream: java.io.InputStream, sampleInterval: Int = 1000): BenchmarkResult {
        val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
        val jsonReader = com.google.gson.stream.JsonReader(reader)
        var parsed = 0L
        val runtime = Runtime.getRuntime()
        var maxUsed = 0L
        val start = System.nanoTime()
        try {
            val peek = jsonReader.peek()
            when (peek) {
                com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                    parsed = parseArrayStream(jsonReader, sampleInterval) { used -> 
                        if (used > maxUsed) maxUsed = used
                    }
                }
                com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                    parsed = parseObjectWithEntries(jsonReader, sampleInterval) { used ->
                        if (used > maxUsed) maxUsed = used
                    }
                }
                else -> throw IllegalArgumentException("Unsupported JSON top-level token: $peek")
            }
        } finally {
            try { jsonReader.close() } catch (_: Throwable) {}
        }
        val dur = (System.nanoTime() - start) / 1_000_000
        return BenchmarkResult(parsed, maxUsed, dur)
    }

    private inline fun parseArrayStream(
        jsonReader: com.google.gson.stream.JsonReader,
        sampleInterval: Int,
        onMemorySample: (Long) -> Unit
    ): Long {
        val runtime = Runtime.getRuntime()
        var parsed = 0L
        jsonReader.beginArray()
        while (jsonReader.hasNext()) {
            com.google.gson.JsonParser().parse(jsonReader)
            parsed++
            if (parsed % sampleInterval == 0L) {
                val used = runtime.totalMemory() - runtime.freeMemory()
                onMemorySample(used)
            }
        }
        jsonReader.endArray()
        return parsed
    }

    private inline fun parseObjectWithEntries(
        jsonReader: com.google.gson.stream.JsonReader,
        sampleInterval: Int,
        onMemorySample: (Long) -> Unit
    ): Long {
        val runtime = Runtime.getRuntime()
        var parsed = 0L
        val rootEl = com.google.gson.JsonParser().parse(jsonReader)
        if (rootEl.isJsonObject && rootEl.asJsonObject.has("entries")) {
            val arr = rootEl.asJsonObject.getAsJsonArray("entries")
            for (el in arr) {
                parsed++
                if (parsed % sampleInterval == 0L) {
                    val used = runtime.totalMemory() - runtime.freeMemory()
                    onMemorySample(used)
                }
            }
        }
        return parsed
    }
}
