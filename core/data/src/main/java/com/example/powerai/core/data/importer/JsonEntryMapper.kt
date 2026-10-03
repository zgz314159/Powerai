package com.example.powerai.core.data.importer

import com.example.powerai.core.model.util.TextSanitizer

import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.util.BlocksTextExtractor
import com.google.gson.JsonParser

/**
 * Converts a parsed JSON entry together with file metadata into a
 * [KnowledgeEntity]. Extracted from the original importer into a standalone
 * helper so [StreamingJsonResourceImporter] stays lean and the transformation
 * is easier to test.
 */
object JsonEntryMapper {
    fun toEntity(
        e: JsonResourceParser.JsonEntry,
        metadata: JsonResourceParser.FileMetadata,
        gson: com.google.gson.Gson
    ): KnowledgeEntity {
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

        val isSplitEntry = (e.entryId?.contains("__p3_split") == true) ||
            (e.jobTitle?.contains("（图") == true)

        val sourceForEntity =
            e.source?.takeIf { it.isNotBlank() }
                ?: metadata.source?.takeIf { it.isNotBlank() }
                ?: metadata.fileId.takeIf { it.isNotBlank() }
                ?: metadata.fileName

        var imageUrisJson: String? = null
        var bboxJson: String? = null
        if (!blocksJson.isNullOrBlank()) {
            try {
                val blocksRoot = JsonParser().parse(blocksJson)
                val imgs = ArrayList<String>()
                // First block with a bbox is the entry's primary PDF locate target.
                var primaryBbox: String? = null

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
                            // bounding box (first block with a box wins)
                            if (primaryBbox == null) {
                                val bb = obj.get("boundingBox") ?: obj.get("bbox")
                                if (bb != null && !bb.isJsonNull) {
                                    primaryBbox = if (bb.isJsonPrimitive) bb.asString else bb.toString()
                                }
                            }

                            for ((_, v) in obj.entrySet()) visit(v)
                        }
                    }
                }

                visit(blocksRoot)
                if (imgs.isNotEmpty()) imageUrisJson = gson.toJson(imgs)
                bboxJson = primaryBbox
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

        val keywordsSerialized = e.tags?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(",")
            ?: ""

        val stablePkSeed = buildString {
            append(metadata.fileId)
            append("::")
            append(e.entryId.orEmpty())
            append("::")
            append(e.jobTitle.orEmpty())
            append("::")
            append(e.position)
        }
        val stableId = ImportUtils.stableId64(stablePkSeed)

        return KnowledgeEntity(
            id = stableId,
            title = e.jobTitle?.trim() ?: e.unitName?.trim() ?: metadata.fileName,
            content = normalizedMarkdown,
            contentNormalized = normalized,
            searchContent = normalized,
            source = sourceForEntity,
            contentBlocksJson = blocksJson,
            pageNumber = e.pageNumber ?: (if (e.position > 0) e.position else null),
            bboxJson = bboxJson,
            imageUris = imageUrisJson,
            category = e.unitName.orEmpty().ifBlank { "未分" },
            keywordsSerialized = keywordsSerialized, packageId = metadata.fileId.takeIf { it.isNotBlank() },
        )
    }
}
