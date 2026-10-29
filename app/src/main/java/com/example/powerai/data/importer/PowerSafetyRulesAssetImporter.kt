package com.example.powerai.data.importer

import android.content.Context
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.model.util.TextSanitizer
import com.google.gson.JsonParser

/**
 * Imports the SparseSearcher flat corpus [ASSET_PATH] into Room so evidence clicks
 * open [KnowledgeDetail] with a real positive id (not AiTextDetail "来源：AI").
 */
internal object PowerSafetyRulesAssetImporter {
    const val ASSET_PATH = "power_safety_rules.json"
    const val DISPLAY_SOURCE = "电力安全工作规程"
    const val DISPLAY_NAME = "电力安全工作规程"

    /** Stable package / imported_files id — same scheme as other built-in assets. */
    val packageId: String = ImportUtils.sha256Hex("asset:$ASSET_PATH")

    /**
     * Idempotent: skip when fingerprint matches; otherwise replace only this package's rows.
     * @return number of knowledge rows owned by this package after the call, or -1 if asset missing.
     */
    suspend fun importIfNeed(
        context: Context,
        dao: KnowledgeDao,
    ): Int {
        val bytes =
            try {
                context.assets.open(ASSET_PATH).use { it.readBytes() }
            } catch (_: Throwable) {
                return -1
            }
        if (bytes.isEmpty()) return -1

        val contentSha = ImportUtils.sha256Hex(bytes)
        val prior = runCatching { dao.getImportedFile(packageId) }.getOrNull()
        val priorStatus = prior?.status?.trim()?.lowercase().orEmpty()
        if (priorStatus == AssetImportStatus.IMPORTED &&
            !prior?.contentSha256.isNullOrBlank() &&
            prior.contentSha256 == contentSha
        ) {
            return dao.countByPackageId(packageId)
        }

        val entities = parseEntities(bytes) ?: return -1
        dao.runInTransaction {
            dao.deleteByPackageId(packageId)
            if (entities.isNotEmpty()) {
                dao.upsertBatch(entities)
            }
            dao.rebuildFts()
            dao.insertImportedFile(
                ImportedFileEntity(
                    fileId = packageId,
                    fileName = DISPLAY_NAME,
                    timestamp = System.currentTimeMillis(),
                    status = AssetImportStatus.IMPORTED,
                    contentSha256 = contentSha,
                ),
            )
        }
        return dao.countByPackageId(packageId)
    }

    internal fun parseEntities(bytes: ByteArray): List<KnowledgeEntity>? {
        return try {
            val root = JsonParser.parseString(String(bytes, Charsets.UTF_8))
            if (!root.isJsonArray) return null
            val arr = root.asJsonArray
            buildList {
                for (i in 0 until arr.size()) {
                    val o = arr[i]?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val title =
                        (
                            o.get("key")?.takeIf { it.isJsonPrimitive }?.asString
                                ?: o.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                                ?: ""
                        ).trim()
                    val content =
                        (o.get("content")?.takeIf { it.isJsonPrimitive }?.asString ?: "").trim()
                    if (title.isBlank() || content.isBlank()) continue
                    val normalized = TextSanitizer.normalizeForSearch(content)
                    val stableId = ImportUtils.stableId64("$packageId::$title")
                    add(
                        KnowledgeEntity(
                            id = stableId,
                            title = title,
                            content = content,
                            source = DISPLAY_SOURCE,
                            category = "安规",
                            contentNormalized = normalized,
                            searchContent = normalized,
                            packageId = packageId,
                            sortOrder = i,
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
