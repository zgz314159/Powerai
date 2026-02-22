package com.example.powerai.data.mappers

import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.domain.model.KnowledgeItem

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
