package com.example.powerai.data.mappers

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.data.entity.KnowledgeEntity

fun KnowledgeEntity.toDomain(): KnowledgeItem = KnowledgeItem(
    id = this.id,
    title = this.title,
    content = this.content,
    source = this.source,
    pageNumber = this.pageNumber,
    category = this.category,
    keywords = if (this.keywordsSerialized.isBlank()) emptyList() else this.keywordsSerialized.split(',').map { it.trim() }
)

fun KnowledgeItem.toEntity(): KnowledgeEntity = KnowledgeEntity(
    id = this.id,
    title = this.title,
    content = this.content,
    contentNormalized = TextSanitizer.normalizeForSearch(this.content),
    searchContent = TextSanitizer.normalizeForSearch(this.content),
    source = this.source,
    pageNumber = this.pageNumber,
    category = this.category,
    keywordsSerialized = this.keywords.joinToString(",")
)
