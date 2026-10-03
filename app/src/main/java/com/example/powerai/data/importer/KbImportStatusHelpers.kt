package com.example.powerai.data.importer

import com.example.powerai.core.data.entity.ImportedFileEntity

/** Derive the diagnostic status shown for an import marker, including the pre-migration case. */
internal fun displayStatus(marker: ImportedFileEntity?): String {
    val raw = marker?.status?.trim()?.lowercase().orEmpty()
    return when {
        raw.isBlank() -> AssetImportStatus.MISSING
        raw == AssetImportStatus.IMPORTED && marker?.contentSha256.isNullOrBlank() -> AssetImportStatus.LEGACY
        else -> raw
    }
}

/** Progress percentage for the asset at [index] out of [total]. */
internal fun progressPercent(
    index: Int,
    total: Int,
): Int =
    if (total <= 0) {
        0
    } else {
        (index.toFloat() / total * PERCENT_SCALE).toInt().coerceIn(0, PERCENT_SCALE)
    }

private const val PERCENT_SCALE = 100
