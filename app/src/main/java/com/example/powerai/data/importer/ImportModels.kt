package com.example.powerai.data.importer

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
