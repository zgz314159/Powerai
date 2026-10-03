package com.example.powerai.core.data.importer

data class ImportProgress(
    val fileId: String,
    val fileName: String,
    val totalItems: Long?,
    val importedItems: Long,
    val percent: Int,
    val status: String,
    val message: String? = null,
)
