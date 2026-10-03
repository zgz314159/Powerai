package com.example.powerai.core.model

data class ImportedFile(
    val fileId: String,
    val fileName: String,
    val timestamp: Long,
    val status: String,
    /** SHA-256 of the imported source bytes; empty for pre-migration (legacy) imports. */
    val contentSha256: String = ""
)
