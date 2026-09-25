package com.example.powerai.core.data.mapper

import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.model.KnowledgeItem

object KnowledgeEntityMapper {
    fun toEntity(item: KnowledgeItem): KnowledgeEntity {
        val normalized = TextSanitizer.normalizeForSearch(item.content)
        return KnowledgeEntity(
            title = item.title,
            content = item.content,
            source = item.source,
            category = item.category,
            keywordsSerialized = if (item.keywords.isEmpty()) "" else item.keywords.joinToString(","),
            contentNormalized = normalized,
            searchContent = normalized,
            pageNumber = item.pageNumber,
            contentBlocksJson = item.contentBlocksJson
        )
    }

    fun toItem(e: KnowledgeEntity): KnowledgeItem {
        val imgCount = try {
            if (e.imageUris.isNullOrBlank()) 0 else org.json.JSONArray(e.imageUris).length()
        } catch (_: Throwable) { 0 }

        return KnowledgeItem(
            id = e.id,
            title = e.title,
            content = e.content,
            source = e.source,
            pageNumber = e.pageNumber,
            category = e.category,
            keywords = if (e.keywordsSerialized.isBlank()) emptyList() else
                e.keywordsSerialized.split(',').map { it.trim() },
            hitBlockIndex = e.pageNumber,
            contentBlocksJson = e.contentBlocksJson,
            imagesCount = imgCount
        )
    }
}
