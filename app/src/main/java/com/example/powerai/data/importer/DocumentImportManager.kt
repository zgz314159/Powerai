package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import com.example.powerai.core.data.dao.EmbeddingDao
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.importer.ImportDefaults
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.importer.NonClosingInputStream
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.VectorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Suppress("LongParameterList")
@Singleton
class DocumentImportManager
    @Inject
    constructor(
        internal val context: Context,
        private val repo: KnowledgeRepository,
        internal val dao: KnowledgeDao,
        internal val scanner: AssetImportScanner,
        private val observability: com.example.powerai.core.model.ObservabilityService,
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
        private val parserFactory: FileParserFactoryType = FileParserFactory,
        internal val visionCacheDao: VisionCacheDao? = null,
        internal val embeddingDao: EmbeddingDao? = null,
        internal val vectorRepository: VectorRepository? = null,
        @Named("vector_index_path") internal val vectorIndexPath: String = "vector_index.bin",
    ) {
        internal val assetImportMutex = Mutex()

        private val _progress = MutableStateFlow<ImportProgress?>(null)
        val progress: StateFlow<ImportProgress?> = _progress

        internal val rebuildStateFlow = MutableStateFlow<KbRebuildState>(KbRebuildState.Idle)
        val rebuildState: StateFlow<KbRebuildState> = rebuildStateFlow

        val importDiagnostics: StateFlow<AssetImportDiagnostics> = scanner.importDiagnostics

        private val uriImporter: UriDocumentImporter by lazy {
            UriDocumentImporter(context, repo, observability, scope, parserFactory) { _progress.value = it }
        }

        suspend fun refreshImportDiagnostics(assetRoot: String = "kb") {
            assetImportMutex.withLock {
                val files =
                    scanner.listAssetFilesRecursive(assetRoot)
                        .filter(scanner::shouldImportAssetJson)
                        .sorted()

                val entries =
                    files.map { assetPath ->
                        val fileId = ImportUtils.sha256Hex("asset:$assetPath")
                        val status = displayStatus(runCatching { dao.getImportedFile(fileId) }.getOrNull())
                        AssetImportDiagnosticEntry(
                            assetPath = assetPath,
                            fileId = fileId,
                            displayName = scanner.assetDisplayName(assetPath),
                            status = status,
                            rowCount = if (status == STATUS_IMPORTED) scanner.countImportedRows(assetPath, fileId) else 0,
                            errorMessage = null,
                        )
                    }
                scanner.publishDiagnostics(assetRoot, entries)
            }
        }

        suspend fun importAssetsIfNeed(assetRoot: String = "kb"): AssetImportOutcome =
            assetImportMutex.withLock {
                val files =
                    scanner.listAssetFilesRecursive(assetRoot)
                        .filter(scanner::shouldImportAssetJson)
                        .sorted()

                if (files.isEmpty()) {
                    scanner.publishDiagnostics(assetRoot, emptyList())
                    return@withLock AssetImportOutcome()
                }

                val diagnostics = buildDiagnostics(files)
                scanner.publishDiagnostics(assetRoot, diagnostics.values)

                val importer = StreamingJsonResourceImporter(dao)
                for ((index, assetPath) in files.withIndex()) {
                    importOneAsset(assetRoot, assetPath, index, files.size, importer, diagnostics)
                }
                summarizeImportOutcome(diagnostics.values, files.size)
            }

        /** User-confirmed rebuild of the built-in KB; see [performBuiltInKnowledgeBaseRebuild]. */
        suspend fun rebuildBuiltInKnowledgeBase(assetRoot: String = "kb"): KbRebuildState = performBuiltInKnowledgeBaseRebuild(assetRoot)

        private suspend fun buildDiagnostics(files: List<String>): LinkedHashMap<String, AssetImportDiagnosticEntry> {
            val diagnostics = LinkedHashMap<String, AssetImportDiagnosticEntry>()
            for (assetPath in files) {
                val fileId = ImportUtils.sha256Hex("asset:$assetPath")
                val status = displayStatus(runCatching { dao.getImportedFile(fileId) }.getOrNull())
                diagnostics[fileId] =
                    AssetImportDiagnosticEntry(
                        assetPath = assetPath,
                        fileId = fileId,
                        displayName = scanner.assetDisplayName(assetPath),
                        status = status,
                        rowCount = if (status == STATUS_IMPORTED) scanner.countImportedRows(assetPath, fileId) else 0,
                        errorMessage = null,
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
        @Suppress("LongParameterList")
        private suspend fun importOneAsset(
            assetRoot: String,
            assetPath: String,
            index: Int,
            total: Int,
            importer: StreamingJsonResourceImporter,
            diagnostics: LinkedHashMap<String, AssetImportDiagnosticEntry>,
        ) {
            val displayName = scanner.assetDisplayName(assetPath)
            val fileId = ImportUtils.sha256Hex("asset:$assetPath")
            val prior = runCatching { dao.getImportedFile(fileId) }.getOrNull()
            val priorStatus = prior?.status?.trim()?.lowercase().orEmpty()

            if (priorStatus == STATUS_IMPORTED) {
                if (prior?.contentSha256.isNullOrBlank()) {
                    diagnostics[fileId] =
                        diagnostics.getValue(fileId).copy(status = STATUS_LEGACY, errorMessage = LEGACY_NOTE)
                    scanner.publishDiagnostics(assetRoot, diagnostics.values)
                    return
                }
                val currentHash = context.assets.open(assetPath).use { ImportUtils.sha256Hex(it) }
                if (currentHash.isNotBlank() && currentHash == prior.contentSha256) {
                    diagnostics[fileId] =
                        diagnostics.getValue(fileId).copy(
                            status = STATUS_IMPORTED,
                            rowCount = scanner.countImportedRows(assetPath, fileId),
                        )
                    scanner.publishDiagnostics(assetRoot, diagnostics.values)
                    return
                }
            }

            publishInProgress(assetRoot, assetPath, fileId, displayName, index, total, diagnostics)
            try {
                replacePackage(assetPath, fileId, displayName, importer)
                diagnostics[fileId] =
                    AssetImportDiagnosticEntry(
                        assetPath = assetPath,
                        fileId = fileId,
                        displayName = displayName,
                        status = STATUS_IMPORTED,
                        rowCount = scanner.countImportedRows(assetPath, fileId),
                        errorMessage = null,
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

        @Suppress("LongParameterList")
        private fun publishInProgress(
            assetRoot: String,
            assetPath: String,
            fileId: String,
            displayName: String,
            index: Int,
            total: Int,
            diagnostics: LinkedHashMap<String, AssetImportDiagnosticEntry>,
        ) {
            diagnostics[fileId] =
                AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = displayName,
                    status = STATUS_IN_PROGRESS,
                    rowCount = diagnostics[fileId]?.rowCount ?: 0,
                    errorMessage = null,
                )
            scanner.publishDiagnostics(assetRoot, diagnostics.values)
            _progress.value =
                ImportProgress(
                    fileId = fileId,
                    fileName = displayName,
                    totalItems = total.toLong(),
                    importedItems = index.toLong(),
                    percent = progressPercent(index, total),
                    status = STATUS_IN_PROGRESS,
                    message = "importing assets: $assetPath",
                )
        }

        /**
         * Replace this package's rows in one transaction: delete only the rows attributed to [fileId],
         * stream the new JSON while fingerprinting the exact bytes read (whole file, drained past the
         * parser), rebuild FTS, then commit the new fingerprint with the marker. Any throw (including
         * cancellation) rolls the whole package back.
         */
        internal suspend fun replacePackage(
            assetPath: String,
            fileId: String,
            displayName: String,
            importer: StreamingJsonResourceImporter,
        ) {
            dao.runInTransaction {
                dao.deleteByPackageId(fileId)
                var contentSha = ""
                context.assets.open(assetPath).use { raw ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hashing = DigestInputStream(raw, digest)
                    importer
                        .importFromJson(
                            inputStream = NonClosingInputStream(hashing),
                            batchSize = ImportDefaults.DEFAULT_BATCH_SIZE,
                            trace = null,
                            fallbackFileName = displayName,
                            fallbackFileId = fileId,
                        ).collect { p ->
                            _progress.value = p.copy(fileId = fileId, fileName = displayName)
                        }
                    // The parser stops at the JSON end; drain the rest so the hash covers the whole file.
                    val buffer = ByteArray(HASH_DRAIN_BUFFER)
                    while (hashing.read(buffer) >= 0) {
                        // drain
                    }
                    // digest.digest() is already the SHA-256 over the whole file; only hex-encode it.
                    contentSha = ImportUtils.hex(digest.digest())
                }
                dao.insertImportedFile(
                    ImportedFileEntity(
                        fileId = fileId,
                        fileName = displayName,
                        timestamp = System.currentTimeMillis(),
                        status = STATUS_IMPORTED,
                        contentSha256 = contentSha,
                    ),
                )
            }
        }

        @Suppress("LongParameterList")
        private suspend fun onImportFailure(
            assetRoot: String,
            assetPath: String,
            fileId: String,
            displayName: String,
            index: Int,
            total: Int,
            priorStatus: String,
            error: Throwable,
            diagnostics: LinkedHashMap<String, AssetImportDiagnosticEntry>,
        ) {
            // A failed update must not destroy a previous good version's "imported" record; only a
            // first-time failure is recorded as failed so the next run retries it.
            if (priorStatus != STATUS_IMPORTED) {
                repo.markFileImported(fileId, displayName, System.currentTimeMillis(), STATUS_FAILED)
            }
            diagnostics[fileId] =
                AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = displayName,
                    status = STATUS_FAILED,
                    rowCount = 0,
                    errorMessage = error.message,
                )
            scanner.publishDiagnostics(assetRoot, diagnostics.values)
            _progress.value =
                ImportProgress(
                    fileId = fileId,
                    fileName = displayName,
                    totalItems = total.toLong(),
                    importedItems = index.toLong(),
                    percent = progressPercent(index, total),
                    status = STATUS_FAILED,
                    message = error.message,
                )
            observability.importFailed(fileId, displayName, error.message)
        }

        fun importUri(
            uri: Uri,
            batchSize: Int = 100,
        ) {
            uriImporter.import(uri, batchSize)
        }

        private companion object {
            const val STATUS_MISSING = AssetImportStatus.MISSING
            const val STATUS_IMPORTED = AssetImportStatus.IMPORTED
            const val STATUS_IN_PROGRESS = AssetImportStatus.IN_PROGRESS
            const val STATUS_FAILED = AssetImportStatus.FAILED
            const val STATUS_LEGACY = AssetImportStatus.LEGACY
            const val LEGACY_NOTE = "pre-migration import without content fingerprint; auto-replace skipped"
            const val HASH_DRAIN_BUFFER = 8192
        }
    }
