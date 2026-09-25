package com.example.powerai.core.data.entity

data class KnowledgeListItemEntity(
    val id: Long,
    val title: String,
    val content: String,
    val source: String,
    val category: String,
    val pageNumber: Int?,
    val sortOrder: Int?,
    val imageUris: String?
)

data class KnowledgeRowPayloadStat(
    val id: Long,
    val source: String,
    val contentLength: Int,
    val contentBlocksLength: Int,
    val bboxLength: Int,
    val imageUrisLength: Int
)
