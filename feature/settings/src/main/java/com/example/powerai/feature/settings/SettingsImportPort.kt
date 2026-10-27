package com.example.powerai.feature.settings

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.core.data.importer.ImportProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow URI-import surface for Settings (Phase C5).
 * Keeps JsonRepository / format parsers in :app.
 */
interface SettingsImportPort {
    val importProgress: StateFlow<ImportProgress?>

    suspend fun importUri(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
    )
}
