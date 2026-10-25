package com.example.powerai.feature.importer

import android.net.Uri
import com.example.powerai.data.importer.UserKbImportProgress
import com.example.powerai.data.importer.UserKbImportResult

/**
 * Narrow user-directory KB import surface for the Importer feature (Phase C6).
 * Keeps UserKbPackageImporter / streaming parsers in :app.
 */
interface UserKbDirectoryImportPort {
    suspend fun importDirectory(
        treeUri: Uri,
        onProgress: (UserKbImportProgress) -> Unit = {},
    ): UserKbImportResult
}
