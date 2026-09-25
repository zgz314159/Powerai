package com.example.powerai.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "embedding_metadata")
data class EmbeddingMetadataEntity(
    @PrimaryKey val id: Long,
    val fileName: String,
    val status: String,
    val createdAt: Long
)
