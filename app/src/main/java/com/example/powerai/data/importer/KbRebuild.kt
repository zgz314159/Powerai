package com.example.powerai.data.importer

import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Aggregate a finished asset pass from its per-asset diagnostics. */
internal fun summarizeImportOutcome(
    entries: Collection<AssetImportDiagnosticEntry>,
    scanned: Int,
): AssetImportOutcome =
    AssetImportOutcome(
        scanned = scanned,
        imported = entries.count { it.status == AssetImportStatus.IMPORTED },
        failed = entries.count { it.status == AssetImportStatus.FAILED },
        legacy = entries.count { it.status == AssetImportStatus.LEGACY },
    )

/**
 * User-confirmed rebuild of the built-in knowledge base.
 *
 * Pre-migration (v5) rows carry `packageId = NULL` and their import markers have an empty
 * fingerprint, so legacy built-in rows cannot be told apart from manual imports. Only an explicit
 * user confirmation may therefore clear the app's knowledge rows, FTS and import markers and
 * re-import the current bundled assets. The whole rebuild runs in a single SQLite transaction: any
 * failure rolls everything back, so no half-rebuilt state is ever committed as a success.
 *
 * Caches keyed by knowledge entity id (vision cache, embedding metadata, the on-disk native vector
 * index) are invalidated because the rows they point at are replaced.
 */
internal suspend fun DocumentImportManager.performBuiltInKnowledgeBaseRebuild(assetRoot: String = "kb"): KbRebuildState =
    assetImportMutex.withLock {
        val files = scanRebuildableAssets(assetRoot)
        if (files.isEmpty()) return@withLock failRebuild("未找到内置知识库资源")

        rebuildStateFlow.value = KbRebuildState.Running(0, files.size, null)
        try {
            val rows = runRebuildTransaction(files)
            deleteVectorIndex()
            scanner.publishDiagnostics(assetRoot, rebuildDiagnostics(files))
            KbRebuildState.Success(knowledgeRows = rows, packages = files.size)
                .also { rebuildStateFlow.value = it }
        } catch (c: CancellationException) {
            rebuildStateFlow.value = KbRebuildState.Cancelled
            throw c
        } catch (_: Throwable) {
            failRebuild("重建失败，请重试")
        }
    }

private suspend fun DocumentImportManager.scanRebuildableAssets(assetRoot: String): List<String> =
    scanner
        .listAssetFilesRecursive(assetRoot)
        .filter(scanner::shouldImportAssetJson)
        .sorted()

/**
 * Clear every app knowledge row, FTS entry, import marker and dependent cache, then re-import all
 * bundled assets inside one transaction. Returns the number of committed knowledge rows.
 */
private suspend fun DocumentImportManager.runRebuildTransaction(files: List<String>): Int {
    val importer = StreamingJsonResourceImporter(dao)
    var rows = 0
    dao.runInTransaction {
        dao.deleteAllKnowledge()
        dao.deleteAllImportedFiles()
        visionCacheDao?.deleteAll()
        embeddingDao?.deleteAll()
        for ((index, assetPath) in files.withIndex()) {
            val fileId = ImportUtils.sha256Hex("asset:$assetPath")
            val displayName = scanner.assetDisplayName(assetPath)
            rebuildStateFlow.value = KbRebuildState.Running(index, files.size, displayName)
            replacePackage(assetPath, fileId, displayName, importer)
            rows += scanner.countImportedRows(assetPath, fileId)
        }
    }
    return rows
}

private suspend fun DocumentImportManager.rebuildDiagnostics(files: List<String>): List<AssetImportDiagnosticEntry> =
    files.map { assetPath ->
        val fileId = ImportUtils.sha256Hex("asset:$assetPath")
        AssetImportDiagnosticEntry(
            assetPath = assetPath,
            fileId = fileId,
            displayName = scanner.assetDisplayName(assetPath),
            status = AssetImportStatus.IMPORTED,
            rowCount = scanner.countImportedRows(assetPath, fileId),
            errorMessage = null,
        )
    }

private fun DocumentImportManager.deleteVectorIndex() {
    runCatching { File(context.filesDir, vectorIndexPath).delete() }
}

private fun DocumentImportManager.failRebuild(reason: String): KbRebuildState.Failed =
    KbRebuildState.Failed(reason).also { rebuildStateFlow.value = it }
