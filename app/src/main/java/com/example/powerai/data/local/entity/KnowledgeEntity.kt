package com.example.powerai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 数据库中存储的知识条目实体。
 */
@Entity(tableName = "knowledge")
data class KnowledgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    val source: String,
    val category: String = "",
    val keywordsSerialized: String = "",

    /** Normalized text used for lexical search (FTS/LIKE). */
    val contentNormalized: String = "",

    /**
     * Search payload used by FTS. Defaults to [contentNormalized].
     * Keeping this field allows evolving search indexing strategy without rewriting callers.
     */
    val searchContent: String = "",

    /** Optional page number (1-based) for PDF/docx extracted entries. */
    val pageNumber: Int? = null,

    /** Optional preprocessed structured blocks payload (JSON). */
    val contentBlocksJson: String? = null,

    /** Optional bounding box info (JSON). */
    val bboxJson: String? = null,

    /** Optional image URIs extracted from blocks (JSON array string). */
    val imageUris: String? = null
)
