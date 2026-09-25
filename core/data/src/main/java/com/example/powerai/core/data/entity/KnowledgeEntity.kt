package com.example.powerai.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 数据库中存储的知识条目实体
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
     * Search payload used by FTS.
     */
    val searchContent: String = "",

    /** Optional page number (1-based) for PDF/docx extracted entries. */
    val pageNumber: Int? = null,

    /** Optional sort order for consistent sequential display. */
    val sortOrder: Int? = null,

    /** Optional preprocessed structured blocks payload (JSON). */
    val contentBlocksJson: String? = null,

    /** Optional bounding box info (JSON). */
    val bboxJson: String? = null,

    /** Optional image URIs extracted from blocks (JSON array string). */
    val imageUris: String? = null,

    /**
     * Checksum of the stored vector.
     */
    @ColumnInfo(name = "vector_checksum")
    val vectorChecksum: String = "",

    /**
     * Timestamp (epoch ms) when this entity was last indexed.
     */
    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long = 0L
)
