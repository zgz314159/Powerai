package com.example.powerai.core.model

data class ImportedFile(
    val fileId: String,
    val fileName: String,
    val timestamp: Long,
    val status: String
)
