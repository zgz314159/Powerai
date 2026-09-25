package com.example.powerai.data.importer

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * Shared parsing logic used by both [JsonResourceImporter] and
 * [StreamingJsonResourceImporter].  Extracted from the original importer
 * to reduce duplication and shrink remaining files.
 */
object JsonResourceParser {
    data class FileMetadata(
        val fileName: String,
        val fileId: String,
        val schemaVersion: String? = null,
        val importTimestamp: Long? = null,
        val source: String? = null,
        val entriesCount: Int = 0,
        val docSha256: String? = null,
        val imagesCount: Int = 0
    )

    data class JsonEntry(
        val entryId: String? = null,
        val unitName: String? = null,
        val jobTitle: String? = null,
        val contentMarkdown: String? = null,
        val contentNormalized: String? = null,
        val blocks: JsonElement? = null,
        val pageNumber: Int? = null,
        val position: Int = 0,
        val tags: List<String>? = null,
        val source: String? = null
    )

    private data class JsonSchema(
        val fileMetadata: FileMetadata,
        val entries: List<JsonEntry>
    )

    private data class LegacySchema(
        val fileName: String? = null,
        val fileId: String? = null,
        val entries: List<JsonEntry> = emptyList()
    )

    /**
     * Inspect a parsed root element and return the metadata plus entries list.
     * Supports multiple legacy shapes as described in the importer.
     */
    fun parseRoot(
        root: JsonElement,
        gson: Gson,
        fallbackFileName: String?,
        fallbackFileId: String?
    ): Pair<FileMetadata, List<JsonEntry>> {
        // if some pipeline accidentally ships a manifest-like object, caller
        // should handle skipping earlier; parser assumes data shape rather than
        // filtering out manifests.

        return when {
            root.isJsonObject && root.asJsonObject.has("fileMetadata") -> {
                val data = gson.fromJson(root, JsonSchema::class.java)
                data.fileMetadata to data.entries
            }
            root.isJsonObject && root.asJsonObject.has("entries") -> {
                val legacy = gson.fromJson(root, LegacySchema::class.java)
                val fileName = legacy.fileName?.takeIf { it.isNotBlank() }
                    ?: fallbackFileName.orEmpty().ifBlank { "assets" }
                val fileId = legacy.fileId?.takeIf { it.isNotBlank() }
                    ?: fallbackFileId.orEmpty().ifBlank { fileName }
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
    }

    /**
     * Helper for legacy array-case parsing (root is JsonArray of entry objects).
     */
    fun parseEntriesArray(arr: JsonArray): List<JsonEntry> {
        val list = mutableListOf<JsonEntry>()
        for (el in arr) {
            try {
                val entry = Gson().fromJson(el, JsonEntry::class.java)
                list.add(entry)
            } catch (_: Throwable) {
                // skip malformed element
            }
        }
        return list
    }
}
