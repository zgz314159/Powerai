package com.example.powerai.domain.model

import com.example.powerai.core.model.KnowledgeItem

data class DatabaseFileGroup(
    val key: String,
    val fileId: String? = null,
    val fileName: String,
    val totalImages: Int,
    val totalRowsCount: Int,
    val totalImageCount: Int,
    val rows: List<DatabaseRow>
)

data class DatabaseRow(
    val item: KnowledgeItem,
    val imagesCount: Int
)

data class DatabaseFocusTarget(
    val itemId: Long,
    val title: String? = null,
    val source: String? = null,
    val pageNumber: Int? = null,
    val content: String? = null
)
