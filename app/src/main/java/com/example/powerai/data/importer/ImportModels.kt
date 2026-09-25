package com.example.powerai.data.importer

data class ImportProgress(
    val fileId: String,
    val fileName: String,
    val totalItems: Long?,
    val importedItems: Long,
    val percent: Int,
    val status: String,
    val message: String? = null
)

data class AssetImportDiagnosticEntry(
    val assetPath: String,
    val fileId: String,
    val displayName: String,
    val status: String,
    val rowCount: Int,
    val errorMessage: String? = null
)

data class AssetImportDiagnostics(
    val assetRoot: String = "kb",
    val scannedCount: Int = 0,
    val importedCount: Int = 0,
    val failedCount: Int = 0,
    val zeroRowCount: Int = 0,
    val entries: List<AssetImportDiagnosticEntry> = emptyList(),
    val lastScanAt: Long = 0L
)
