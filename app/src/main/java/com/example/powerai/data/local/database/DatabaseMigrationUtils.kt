package com.example.powerai.data.local.database

import android.util.Log
import com.example.powerai.BuildConfig
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.local.dao.KnowledgeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseMigrationUtils {
    private const val TAG = "DbSelfHeal"

    /**
     * Scan for legacy entries without `contentNormalized` and fill them by sanitizing the
     * existing `content` using `TextSanitizer`.
     */
    suspend fun fillMissingNormalizedData(dao: KnowledgeDao) {
        // Perform DB reads/writes on IO dispatcher
        withContext(Dispatchers.IO) {
            val missingCount = try {
                dao.countEntriesMissingNormalized()
            } catch (_: Throwable) {
                0
            }
            if (missingCount <= 0) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "skip: no missing normalized rows")
                }
                return@withContext
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "start: missingRows=$missingCount")
            }

            val legacyEntries = try {
                dao.getEntriesMissingNormalized()
            } catch (t: Throwable) {
                emptyList()
            }

            var fixedCount = 0

            for (e in legacyEntries) {
                try {
                    val normalized = TextSanitizer.normalizeForSearch(e.content)
                    dao.updateNormalizedContent(e.id, normalized)
                    fixedCount++
                } catch (_: Throwable) {
                    // ignore per-row failures; continue with others
                }
            }

            // Finally, attempt to rebuild FTS via DAO helper if available.
            try {
                dao.rebuildFts()
            } catch (_: Throwable) {
                // if DAO rebuild is not available at this point, higher-level maintenance should run it.
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "done: fixedRows=$fixedCount, missingRowsBefore=$missingCount")
            }
        }
    }
}
