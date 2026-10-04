package com.example.powerai.data.importer

import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

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
 * Which knowledge a rebuild replaces.
 *
 * [BUILT_IN] only refreshes packages that are unambiguously owned by a currently bundled asset
 * (the marker's `packageId`/rows encode the asset path), leaving user packages, manual imports and
 * pre-migration rows whose ownership cannot be proven untouched. [ALL] is the explicit "clear
 * everything" escape hatch the user must confirm; it wipes every row, marker and cache first.
 */
internal enum class KbRebuildScope {
    BUILT_IN,
    ALL,
}

/** Committed outcome of one rebuild transaction: rows, imported package count and diagnostics. */
private class RebuildReport(
    val rows: Int,
    val importedPackages: Int,
    val diagnostics: List<AssetImportDiagnosticEntry>,
)

/**
 * Rebuild the built-in knowledge base inside a single SQLite transaction.
 *
 * Ownership rule: a package is only treated as built-in when it is attributed to a currently
 * bundled asset through its `packageId` (`sha256("asset:$assetPath")`, a bare hash — never a
 * `user:` id and never guessed from the PDF sha, source or file name). Legacy (pre-migration)
 * markers carry no fingerprint, so their rows cannot be attributed and are preserved.
 *
 * Caches keyed by knowledge entity id (vision cache, embedding metadata) are invalidated for exactly
 * the rows that are replaced; the whole app-private native vector index is cleared afterwards
 * because it is a single derived cache and can hold ids that no longer have a row.
 */
internal suspend fun DocumentImportManager.performKbRebuild(
    scope: KbRebuildScope,
    assetRoot: String = "kb",
): KbRebuildState =
    assetImportMutex.withLock {
        val files = scanRebuildableAssets(assetRoot)
        if (files.isEmpty()) return@withLock failRebuild("未找到内置知识库资源")

        rebuildStateFlow.value = KbRebuildState.Running(0, files.size, null)
        try {
            val report = runRebuildTransaction(files, scope)
            // Only invalidate the derived index when rows were actually replaced; a no-op rebuild
            // (e.g. a legacy-only install where every package is preserved) must not degrade the
            // index that still matches the live rows.
            val indexOk =
                report.importedPackages == 0 ||
                    invalidateNativeVectorIndex(context, vectorRepository, vectorIndexPath)
            if (indexOk) {
                scanner.publishDiagnostics(assetRoot, report.diagnostics)
                KbRebuildState.Success(knowledgeRows = report.rows, packages = report.importedPackages)
                    .also { rebuildStateFlow.value = it }
            } else {
                // The database committed but the stale on-disk index could not be removed: do not
                // report a complete success, so the user can retry.
                failRebuild("旧索引清理失败，请重试")
            }
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
 * Replace the confirmable built-in packages inside one transaction and re-import the bundled
 * assets. Returns the number of committed knowledge rows plus per-asset diagnostics.
 */
private suspend fun DocumentImportManager.runRebuildTransaction(
    files: List<String>,
    scope: KbRebuildScope,
): RebuildReport {
    val importer = StreamingJsonResourceImporter(dao)
    val diagnostics = ArrayList<AssetImportDiagnosticEntry>(files.size)
    var rows = 0
    var importedPackages = 0
    dao.runInTransaction {
        if (scope == KbRebuildScope.ALL) {
            // Explicit "clear all": every row, marker and id-keyed cache goes, including user
            // packages and unconfirmable legacy data.
            dao.deleteAllKnowledge()
            dao.deleteAllImportedFiles()
            visionCacheDao?.deleteAll()
            embeddingDao?.deleteAll()
        }
        for ((index, assetPath) in files.withIndex()) {
            val fileId = ImportUtils.sha256Hex("asset:$assetPath")
            val displayName = scanner.assetDisplayName(assetPath)
            rebuildStateFlow.value = KbRebuildState.Running(index, files.size, displayName)

            if (scope == KbRebuildScope.BUILT_IN && isLegacyMarker(dao.getImportedFile(fileId))) {
                // Pre-migration install: its rows are unattributed and cannot be proven to belong to
                // this asset. Preserve them untouched; only "clear all" may remove them.
                diagnostics += legacyEntry(assetPath, fileId, displayName)
                continue
            }

            val ownedIds = dao.getIdsByPackageId(fileId)
            replacePackage(assetPath, fileId, displayName, importer)
            invalidateEntityCachesFor(visionCacheDao, embeddingDao, ownedIds)
            val rowCount = scanner.countImportedRows(assetPath, fileId)
            rows += rowCount
            importedPackages++
            diagnostics +=
                AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = displayName,
                    status = AssetImportStatus.IMPORTED,
                    rowCount = rowCount,
                    errorMessage = null,
                )
        }
    }
    return RebuildReport(rows, importedPackages, diagnostics)
}

/** A legacy marker is an "imported" file with no content fingerprint (pre-migration install). */
private fun isLegacyMarker(prior: ImportedFileEntity?): Boolean =
    prior?.status?.trim()?.lowercase() == AssetImportStatus.IMPORTED &&
        prior.contentSha256.isNullOrBlank()

private fun legacyEntry(
    assetPath: String,
    fileId: String,
    displayName: String,
): AssetImportDiagnosticEntry =
    AssetImportDiagnosticEntry(
        assetPath = assetPath,
        fileId = fileId,
        displayName = displayName,
        status = AssetImportStatus.LEGACY,
        rowCount = 0,
        errorMessage = KB_LEGACY_NOTE,
    )

private fun DocumentImportManager.failRebuild(reason: String): KbRebuildState.Failed =
    KbRebuildState.Failed(reason).also { rebuildStateFlow.value = it }
