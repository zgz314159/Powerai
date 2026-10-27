package com.example.powerai.feature.database

import android.net.Uri
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.data.importer.AssetImportDiagnostics
import com.example.powerai.data.importer.KbRebuildState
import com.example.powerai.data.importer.UserKbImportResult
import com.example.powerai.data.importer.UserKbPackageRemovalResult
import com.example.powerai.data.importer.UserKbPackageSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow app-side KB import/rebuild surface for the Database feature (Phase C4).
 */
interface DatabaseKbPort {
    val progress: StateFlow<ImportProgress?>
    val importDiagnostics: StateFlow<AssetImportDiagnostics>
    val rebuildState: StateFlow<KbRebuildState>
    val userPackages: StateFlow<List<UserKbPackageSummary>>

    suspend fun importAssetsIfNeed()

    suspend fun rebuildBuiltInKnowledgeBase()

    suspend fun clearAllKnowledgeBases()

    suspend fun refreshImportDiagnostics()

    suspend fun refreshPackages()

    suspend fun removePackage(packageId: String): UserKbPackageRemovalResult

    suspend fun importDirectory(treeUri: Uri): UserKbImportResult

    /** One-click update via persisted SAF tree URI (KI-57); fails if URI/grant missing. */
    suspend fun updatePackageInPlace(packageId: String): UserKbImportResult
}
