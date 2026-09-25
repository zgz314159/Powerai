package com.example.powerai.domain.util

import com.example.powerai.core.model.util.BlocksTextExtractor
import com.example.powerai.core.model.util.BlocksJsonUtils
import com.example.powerai.util.PdfSourceRef
import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object ContextualSearchTextBuilder {
    fun buildSearchPayload(
        title: String,
        source: String,
        category: String,
        pageNumber: Int?,
        keywords: List<String>,
        plainText: String
    ): String {
        val article = extractArticleSignature(title, plainText)
        return buildString {
            append(title.trim())
            if (source.isNotBlank()) {
                append('\n')
                append(source.trim())
            }
            if (category.isNotBlank()) {
                append('\n')
                append(category.trim())
            }
            if (article.isNotBlank()) {
                append('\n')
                append(article)
            }
            pageNumber?.let {
                append("\n")
                append(it)
                append("")
            }
            if (keywords.isNotEmpty()) {
                append('\n')
                append(keywords.joinToString(" "))
            }
            if (plainText.isNotBlank()) {
                append('\n')
                append(plainText.trim())
            }
        }.trim()
    }

    fun contextualizeBlocksJson(
        blocksJson: String,
        title: String,
        source: String,
        category: String,
        pageNumber: Int?
    ): String {
        val root = BlocksJsonUtils.parseRoot(blocksJson) ?: return blocksJson
        val blocks = BlocksJsonUtils.extractBlocksArray(root) ?: return blocksJson
        val contextualLabel = buildContextLabel(title, source, category, pageNumber)
        if (contextualLabel.isBlank()) return blocksJson

        val updated = JsonArray()
        val existingIds = BlocksTextExtractor.computeStableBlockIds(blocksJson)
        for (index in 0 until blocks.size()) {
            val element = blocks[index]
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().apply {
                add("text", element.deepCopy())
            }
            val copy = obj.deepCopy()
            val existingId = copy.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                ?: copy.get("blockId")?.takeIf { it.isJsonPrimitive }?.asString
            if (existingId.isNullOrBlank()) {
                copy.addProperty("id", existingIds.getOrNull(index).orEmpty().ifBlank { "b_ctx_$index" })
            }
            if (!copy.has("contextLabel")) {
                copy.addProperty("contextLabel", contextualLabel)
            }
            updated.add(copy)
        }
        return updated.toString()
    }

    fun buildContextLabel(
        title: String,
        source: String,
        category: String,
        pageNumber: Int?
    ): String {
        return PdfSourceRef.buildUserVisibleLabel(
            title.trim().takeIf { it.isNotBlank() },
            PdfSourceRef.userVisibleSource(source),
            category.trim().takeIf { it.isNotBlank() },
            pageNumber?.let { " · p${it}" }
        )
    }

    private fun extractArticleSignature(title: String, plainText: String): String {
        val match = Regex("([一二三四五六七八九十百0-9]+)").find("$title $plainText") ?: return ""
        return match.value
    }
}
