package com.example.powerai.core.data.database

import com.example.powerai.core.data.BuildConfig
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.model.util.TextSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseMigrationUtils {
    private const val TAG = "DbSelfHeal"

    /**
     * Scan for legacy entries without `contentNormalized` and fill them by sanitizing the
     * existing `content` using `TextSanitizer`.
     */
    suspend fun fillMissingNormalizedData(dao: KnowledgeDao) {
        withContext(Dispatchers.IO) {
            val missingCount = try {
                dao.countEntriesMissingNormalized()
            } catch (_: Throwable) {
                0
            }
            if (missingCount <= 0) {
                if (BuildConfig.DEBUG) {
                    // log removed: skip: no missing normalized rows
                }
                return@withContext
            }

            if (BuildConfig.DEBUG) {
                // log removed: start: missingRows=$missingCount
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

            try {
                dao.rebuildFts()
            } catch (_: Throwable) {
                // if DAO rebuild is not available at this point, higher-level maintenance should run it.
            }

            if (BuildConfig.DEBUG) {
                // log removed: done: fixedRows=$fixedCount, missingRowsBefore=$missingCount
            }
        }
    }
}
