package com.example.powerai.core.data.importer

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Helper responsible for converting a JSON entry object into a populated
 * [StreamingJsonResourceImporter.EntityBuilder].  Extraction of this logic
 * keeps the importer loop lean and makes unit testing easier.
 */
internal object StreamingJsonEntryParser {
    /**
     * Populate [builder] using fields from [obj].  Returns `false` if the
     * entry should be skipped (duplicate id, split entry, etc.).
     *
     * Internally we convert the JSON object to a [JsonEntry] and delegate most
     * of the transformation to [JsonEntryMapper].  This keeps the streaming
     * parser lightweight and ensures the two import paths stay aligned.
     */
    fun fillBuilder(
        obj: JsonObject,
        builder: EntityBuilder,
        existingIds: MutableSet<Long>,
        fallbackMeta: JsonResourceParser.FileMetadata,
        imageUriRewriter: ((String) -> String)? = null,
    ): Boolean {
        // User-directory imports rewrite relative shot paths to stable app-private URIs in place
        // before mapping, so the entry's blocks and extracted image uris stay in sync.
        if (imageUriRewriter != null) {
            JsonImageRefRewriter.rewrite(obj, imageUriRewriter)
        }

        // parse entry with Gson (new instance is cheap here)
        val entry = Gson().fromJson(obj, JsonResourceParser.JsonEntry::class.java)

        // mapper will compute stable id and populate all fields. A KB-declared
        // source (e.g. "pdf:{sha256}::{name}") is carried in fallbackMeta.source so
        // it survives the streaming import instead of the asset-path fallback id.
        val entity = JsonEntryMapper.toEntity(entry, fallbackMeta, Gson())

        if (existingIds.contains(entity.id)) {
            return false
        }
        existingIds.add(entity.id)

        // copy mapped values into builder (we still reuse pool)
        builder.id = entity.id
        builder.title = entity.title
        builder.content = entity.content
        builder.contentNormalized = entity.contentNormalized
        builder.searchContent = entity.searchContent
        builder.source = entity.source
        builder.contentBlocksJson = entity.contentBlocksJson
        builder.pageNumber = entity.pageNumber
        builder.bboxJson = entity.bboxJson
        builder.imageUris = entity.imageUris
        builder.category = entity.category
        builder.keywordsSerialized = entity.keywordsSerialized
        builder.packageId = entity.packageId

        return true
    }

    private fun JsonObject.getAsNullableString(name: String): String? {
        return if (has(name) && !get(name).isJsonNull) get(name).asString else null
    }

    private fun JsonObject.getAsNullableInt(name: String): Int? {
        return if (has(name) && !get(name).isJsonNull) {
            try {
                get(name).asInt
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }
}
