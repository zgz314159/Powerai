package com.example.powerai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imported_files")
data class ImportedFileEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val timestamp: Long,
    val status: String // e.g., "imported", "failed", "in_progress"
)
