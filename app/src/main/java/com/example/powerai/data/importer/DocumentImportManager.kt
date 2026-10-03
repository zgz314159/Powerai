package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.importer.ImportDefaults
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.importer.NonClosingInputStream
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import com.example.powerai.core.repository.KnowledgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentImportManager @Inject constructor(
    private val context: Context,
    private val repo: KnowledgeRepository,
    private val dao: KnowledgeDao,
    private val scanner: AssetImportScanner,
    private val observability: com.example.powerai.core.model.ObservabilityService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val parserFactory: FileParserFactoryType = FileParserFactory
) {
    private val assetImportMutex = Mutex()

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress

    val importDiagnostics: StateFlow<AssetImportDiagnostics> = scanner.importDiagnostics

    suspend fun refreshImportDiagnostics(assetRoot: String = "kb") {
        assetImportMutex.withLock {
            val files = scanner.listAssetFilesRecursive(assetRoot)
                .filter(scanner::shouldImportAssetJson)
                .sorted()

            val entries = files.map { assetPath ->
                val fileId = ImportUtils.sha256Hex("asset:$assetPath")
                val status = displayStatus(runCatching { dao.getImportedFile(fileId) }.getOrNull())
                AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = scanner.assetDisplayName(assetPath),
                    status = status,
                    rowCount = if (status == STATUS_IMPORTED) scanner.countImportedRows(assetPath, fileId) else 0,
                    errorMessage = null
                )
            }
            scanner.publishDiagnostics(assetRoot, entries)
        }
    }

    suspend fun importAssetsIfNeed(assetRoot: String = "kb") {
        assetImportMutex.withLock {
            val files = scanner.listAssetFilesRecursive(assetRoot)
                .filter(scanner::shouldImportAssetJson)
                .sorted()

            if (files.isEmpty()) {
                scanner.publishDiagnostics(assetRoot, emptyList())
                return
            }

            val diagnostics = buildDiagnostics(assetRoot, files)
            scanner.publishDiagnostics(assetRoot, diagnostics.values)

            val importer = StreamingJsonResourceImporter(dao)
            for ((index, assetPath) in files.withIndex()) {
                importOneAsset(assetRoot, assetPath, index, files.size, importer, diagnostics)
            }
        }
    }

    private suspend fun buildDiagnostics(
        assetRoot: String,
        files: List<String>
    ): LinkedHashMap<String, AssetImportDiagnosticEntry> {
        val diagnostics = LinkedHashMap<String, AssetImportDiagnosticEntry>()
        for (assetPath in files) {
            val fileId = ImportUtils.sha256Hex("asset:$assetPath")
            val status = displayStatus(runCatching { dao.getImportedFile(fileId) }.getOrNull())
            diagnostics[fileId] = AssetImportDiagnosticEntry(
                assetPath = assetPath,
                fileId = fileId,
                displayName = scanner.assetDisplayName(assetPath),
                status = status,
                rowCount = if (status == STATUS_IMPORTED) scanner.countImportedRows(assetPath, fileId) else 0,
                errorMessage = null
            )
        }
        return diagnostics
    }

    /**
     * Import or replace a single asset.
     *
     * * unchanged content (same fingerprint) → skip, no DB writes;
     * * a pre-migration import with no fingerprint (legacy) → never auto-replace, its rows cannot
     *   be attributed so a replace could mix old and new rows or touch other packages;
     * * otherwise → atomically replace only this package's rows.
     */
    private suspend fun importOneAsset(
        assetRoot: String,
        assetPath: String,
        index: Int,
        total: Int,
        importer: StreamingJsonResourceImporter,
        diagnostics: LinkedHashMap<String, AssetImportDiagnosticEntry>
    ) {
        val displayName = scanner.assetDisplayName(assetPath)
        val fileId = ImportUtils.sha256Hex("asset:$assetPath")
        val prior = runCatching { dao.getImportedFile(fileId) }.getOrNull()
        val priorStatus = prior?.status?.trim()?.lowercase().orEmpty()

        if (priorStatus == STATUS_IMPORTED) {
            if (prior?.contentSha256.isNullOrBlank()) {
                diagnostics[fileId] = diagnostics.getValue(fileId).copy(
                    status = STATUS_LEGACY,
                    errorMessage = LEGACY_NOTE
                )
                scanner.publishDiagnostics(assetRoot, diagnostics.values)
                return
            }
            val currentHash = context.assets.open(assetPath).use { ImportUtils.sha256Hex(it) }
            if (currentHash.isNotBlank() && currentHash == prior.contentSha256) {
                diagnostics[fileId] = diagnostics.getValue(fileId).copy(
                    status = STATUS_IMPORTED,
                    rowCount = scanner.countImportedRows(assetPath, fileId)
                )
                scanner.publishDiagnostics(assetRoot, diagnostics.values)
                return
            }
        }

        publishInProgress(assetRoot, assetPath, fileId, displayName, index, total, diagnostics)
        try {
            replacePackage(assetPath, fileId, displayName, importer)
            diagnostics[fileId] = AssetImportDiagnosticEntry(
                assetPath = assetPath,
                fileId = fileId,
                displayName = displayName,
                status = STATUS_IMPORTED,
                rowCount = scanner.countImportedRows(assetPath, fileId),
                errorMessage = null
            )
            scanner.publishDiagnostics(assetRoot, diagnostics.values)
            observability.importCompleted(fileId, displayName, 0)
        } catch (c: CancellationException) {
            // Cancellation must propagate instead of being recorded as a failed import.
            throw c
        } catch (t: Throwable) {
            onImportFailure(assetRoot, assetPath, fileId, displayName, index, total, priorStatus, t, diagnostics)
        }
    }

    private fun publishInProgress(
        assetRoot: String,
        assetPath: String,
        fileId: String,
        displayName: String,
        index: Int,
        total: Int,
        diagnostics: LinkedHashMap<String, AssetImportDiagnosticEntry>
    ) {
        diagnostics[fileId] = AssetImportDiagnosticEntry(
            assetPath = assetPath,
            fileId = fileId,
            displayName = displayName,
            status = STATUS_IN_PROGRESS,
            rowCount = diagnostics[fileId]?.rowCount ?: 0,
            errorMessage = null
        )
        scanner.publishDiagnostics(assetRoot, diagnostics.values)
        _progress.value = ImportProgress(
            fileId = fileId,
            fileName = displayName,
            totalItems = total.toLong(),
            importedItems = index.toLong(),
            percent = progressPercent(index, total),
            status = STATUS_IN_PROGRESS,
            message = "importing assets: $assetPath"
        )
    }

    /**
     * Replace this package's rows in one transaction: delete only the rows attributed to [fileId],
     * stream the new JSON while fingerprinting the exact bytes read (whole file, drained past the
     * parser), rebuild FTS, then commit the new fingerprint with the marker. Any throw (including
     * cancellation) rolls the whole package back.
     */
    private suspend fun replacePackage(
        assetPath: String,
        fileId: String,
        displayName: String,
        importer: StreamingJsonResourceImporter
    ) {
        dao.runInTransaction {
            dao.deleteByPackageId(fileId)
            var contentSha = ""
            context.assets.open(assetPath).use { raw ->
                val digest = MessageDigest.getInstance("SHA-256")
                val hashing = DigestInputStream(raw, digest)
                importer.importFromJson(
                    inputStream = NonClosingInputStream(hashing),
                    batchSize = ImportDefaults.DEFAULT_BATCH_SIZE,
                    trace = null,
                    fallbackFileName = displayName,
                    fallbackFileId = fileId
                ).collect { p ->
                    _progress.value = p.copy(fileId = fileId, fileName = displayName)
                }
                // The parser stops at the JSON end; drain the rest so the hash covers the whole file.
                val buffer = ByteArray(HASH_DRAIN_BUFFER)
                while (hashing.read(buffer) >= 0) {
                    // drain
                }
                contentSha = ImportUtils.sha256Hex(digest.digest())
            }
            dao.insertImportedFile(
                ImportedFileEntity(
                    fileId = fileId,
                    fileName = displayName,
                    timestamp = System.currentTimeMillis(),
                    status = STATUS_IMPORTED,
                    contentSha256 = contentSha
                )
            )
        }
    }

    private suspend fun onImportFailure(
        assetRoot: String,
        assetPath: String,
        fileId: String,
        displayName: String,
        index: Int,
        total: Int,
        priorStatus: String,
        error: Throwable,
        diagnostics: LinkedHashMap<String, AssetImportDiagnosticEntry>
    ) {
        // A failed update must not destroy a previous good version's "imported" record; only a
        // first-time failure is recorded as failed so the next run retries it.
        if (priorStatus != STATUS_IMPORTED) {
            repo.markFileImported(fileId, displayName, System.currentTimeMillis(), STATUS_FAILED)
        }
        diagnostics[fileId] = AssetImportDiagnosticEntry(
            assetPath = assetPath,
            fileId = fileId,
            displayName = displayName,
            status = STATUS_FAILED,
            rowCount = 0,
            errorMessage = error.message
        )
        scanner.publishDiagnostics(assetRoot, diagnostics.values)
        _progress.value = ImportProgress(
            fileId = fileId,
            fileName = displayName,
            totalItems = total.toLong(),
            importedItems = index.toLong(),
            percent = progressPercent(index, total),
            status = STATUS_FAILED,
            message = error.message
        )
        observability.importFailed(fileId, displayName, error.message)
    }

    private fun displayStatus(marker: ImportedFileEntity?): String {
        val raw = marker?.status?.trim()?.lowercase().orEmpty()
        if (raw.isBlank()) return STATUS_MISSING
        if (raw == STATUS_IMPORTED && marker?.contentSha256.isNullOrBlank()) return STATUS_LEGACY
        return raw
    }

    private fun progressPercent(index: Int, total: Int): Int =
        if (total <= 0) 0 else ((index * 100f) / total).toInt().coerceIn(0, 100)

    fun importUri(uri: Uri, batchSize: Int = 100) {
        scope.launch {
            val resolver = context.contentResolver
            val fileName = uri.lastPathSegment ?: "imported"
            try {
                val parser = parserFactory.create(fileName, resolver)
                var imported = 0L
                val fileId = parser.parse(uri, fileName, batchSize, onBatchReady = { batch ->
                    val items = batch.map { e ->
                        com.example.powerai.core.model.KnowledgeItem(
                            id = 0L,
                            title = e.title,
                            content = e.content,
                            source = e.source,
                            category = e.category,
                            keywords = emptyList()
                        )
                    }
                    repo.insertBatch(items)
                    imported += batch.size
                    _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = imported, percent = 0, status = "in_progress")
                })

                observability.importStarted(fileId, fileName)
                val already = repo.isFileImported(fileId)
                if (already) {
                    _progress.value = ImportProgress(fileId = fileId, fileName = fileName, totalItems = null, importedItems = 0, percent = 100, status = "skipped", message = "already imported")
                } else {
                    repo.markFileImported(fileId, fileName, System.currentTimeMillis(), "imported")
                    _progress.value = ImportProgress(fileId = fileId, fileName = fileName, totalItems = null, importedItems = 0, percent = 100, status = "imported")
                    observability.importCompleted(fileId, fileName, 0)
                }
            } catch (e: Exception) {
                _progress.value = ImportProgress(fileId = "", fileName = fileName, totalItems = null, importedItems = 0, percent = 0, status = "failed", message = e.message)
                observability.importFailed("", fileName, e.message)
            }
        }
    }

    private companion object {
        const val STATUS_MISSING = "missing"
        const val STATUS_IMPORTED = "imported"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_FAILED = "failed"
        const val STATUS_LEGACY = "legacy"
        const val LEGACY_NOTE = "pre-migration import without content fingerprint; auto-replace skipped"
        const val HASH_DRAIN_BUFFER = 8192
    }
}
