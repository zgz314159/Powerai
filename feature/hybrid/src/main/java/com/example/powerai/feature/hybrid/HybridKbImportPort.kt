package com.example.powerai.feature.hybrid

import android.net.Uri
import com.example.powerai.core.data.importer.ImportProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow import surface used by Hybrid UI / ViewModel (Phase C1).
 * Implemented by app DocumentImportManager.
 */
interface HybridKbImportPort {
    val progress: StateFlow<ImportProgress?>

    /** Ensures built-in KB assets are imported (failures surface via [progress] / thrown errors). */
    suspend fun ensureAssetsImported(assetRoot: String = "kb")

    fun importUri(
        uri: Uri,
        batchSize: Int = 100,
    )
}
