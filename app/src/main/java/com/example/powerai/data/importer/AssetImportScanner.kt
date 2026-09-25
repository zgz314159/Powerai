package com.example.powerai.data.importer

import android.content.Context
import com.example.powerai.core.data.dao.KnowledgeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetImportScanner @Inject constructor(
    private val context: Context,
    private val dao: KnowledgeDao
) {
    private val _importDiagnostics = MutableStateFlow(AssetImportDiagnostics())
    val importDiagnostics: StateFlow<AssetImportDiagnostics> = _importDiagnostics

    suspend fun listAssetFilesRecursive(root: String): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>(256)
        fun walk(path: String) {
            val children = try {
                context.assets.list(path)?.toList().orEmpty()
            } catch (_: Throwable) {
                emptyList()
            }
            if (children.isEmpty()) {
                // If AssetManager.list() returns empty, it can be either a file or an empty dir.
                // We treat it as a file candidate.
                out.add(path)
                return
            }
            for (name in children) {
                val child = if (path.isBlank()) name else "$path/$name"
                walk(child)
            }
        }
        walk(root)
        out
    }

    fun shouldImportAssetJson(path: String): Boolean {
        val lower = path.lowercase()
        if (!lower.endsWith(".json")) return false
        if (lower.endsWith("/manifest.json")) return false
        if (!lower.contains("/knowledge_base")) return false
        if (lower.endsWith(".tmp.json")) return false
        if (lower.endsWith(".ultra_clean.json")) return false
        return true
    }

    fun assetDisplayName(assetPath: String): String {
        val normalized = assetPath.replace('\\', '/')
        return normalized.substringBeforeLast('/', normalized)
            .substringAfterLast('/')
            .ifBlank { normalized.substringAfterLast('/') }
    }

    suspend fun countImportedRows(assetPath: String, fileId: String): Int {
        val sourcePrefix = KbAssetPathNormalizer.sourcePrefixForAsset(assetPath)
        val bySource = sourcePrefix?.let { prefix ->
            runCatching { dao.countBySourcePrefix(prefix) }.getOrDefault(0)
        } ?: 0
        if (bySource > 0) return bySource
        return runCatching { dao.countBySourcePrefix(fileId) }.getOrDefault(0)
    }

    fun publishDiagnostics(assetRoot: String, entries: Collection<AssetImportDiagnosticEntry>) {
        val list = entries.toList()
        _importDiagnostics.value = AssetImportDiagnostics(
            assetRoot = assetRoot,
            scannedCount = list.size,
            importedCount = list.count { it.status == "imported" },
            failedCount = list.count { it.status == "failed" },
            zeroRowCount = list.count { it.status == "imported" && it.rowCount <= 0 },
            entries = list,
            lastScanAt = System.currentTimeMillis()
        )
    }
}
