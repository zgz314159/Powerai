package com.example.powerai.data.importer

import android.net.Uri
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.feature.database.DatabaseKbPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDatabaseKbPort
    @Inject
    constructor(
        private val importManager: DocumentImportManager,
        private val userKbPackageImporter: UserKbPackageImporter,
    ) : DatabaseKbPort {
        override val progress: StateFlow<ImportProgress?> = importManager.progress
        override val importDiagnostics: StateFlow<AssetImportDiagnostics> = importManager.importDiagnostics
        override val rebuildState: StateFlow<KbRebuildState> = importManager.rebuildState
        override val userPackages: StateFlow<List<UserKbPackageSummary>> = userKbPackageImporter.packages

        override suspend fun importAssetsIfNeed() {
            importManager.importAssetsIfNeed()
        }

        override suspend fun rebuildBuiltInKnowledgeBase() {
            importManager.rebuildBuiltInKnowledgeBase()
        }

        override suspend fun clearAllKnowledgeBases() {
            importManager.clearAllKnowledgeBases()
        }

        override suspend fun refreshImportDiagnostics() {
            importManager.refreshImportDiagnostics()
        }

        override suspend fun refreshPackages() {
            userKbPackageImporter.refreshPackages()
        }

        override suspend fun removePackage(packageId: String): UserKbPackageRemovalResult = userKbPackageImporter.removePackage(packageId)

        override suspend fun importDirectory(treeUri: Uri): UserKbImportResult = userKbPackageImporter.importDirectory(treeUri)

        override suspend fun updatePackageInPlace(packageId: String): UserKbImportResult =
            userKbPackageImporter.updateFromPersistedTreeUri(packageId)
    }
