package com.example.powerai.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imported_files")
data class ImportedFileEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val timestamp: Long,
    val status: String,
    /**
     * SHA-256 of the imported source bytes. Empty for pre-migration (legacy) imports, which
     * therefore cannot be proven unchanged and are never auto-replaced.
     */
    @ColumnInfo(name = "contentSha256", defaultValue = "")
    val contentSha256: String = ""
)
