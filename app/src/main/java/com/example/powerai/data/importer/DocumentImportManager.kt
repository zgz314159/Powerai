package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.repository.KnowledgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                val status = runCatching {
                    dao.getImportedFileStatus(fileId)?.trim()?.lowercase().orEmpty()
                }.getOrDefault("")
                AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = scanner.assetDisplayName(assetPath),
                    status = if (status.isBlank()) "missing" else status,
                    rowCount = if (status == "imported") scanner.countImportedRows(assetPath, fileId) else 0,
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

            val importer = StreamingJsonResourceImporter(dao)
            val diagnostics = LinkedHashMap<String, AssetImportDiagnosticEntry>()

            for (assetPath in files) {
                val fileId = ImportUtils.sha256Hex("asset:$assetPath")
                val status = try {
                    dao.getImportedFileStatus(fileId)?.trim()?.lowercase().orEmpty()
                } catch (_: Throwable) {
                    ""
                }
                diagnostics[fileId] = AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = scanner.assetDisplayName(assetPath),
                    status = if (status.isBlank()) "missing" else status,
                    rowCount = if (status == "imported") scanner.countImportedRows(assetPath, fileId) else 0,
                    errorMessage = null
                )
            }
            scanner.publishDiagnostics(assetRoot, diagnostics.values)

            for ((index, assetPath) in files.withIndex()) {
                val displayName = scanner.assetDisplayName(assetPath)
                val fileId = ImportUtils.sha256Hex("asset:$assetPath")
                val currentStatus = try {
                    dao.getImportedFileStatus(fileId)?.trim()?.lowercase().orEmpty()
                } catch (_: Throwable) {
                    ""
                }
                if (currentStatus == "imported") continue

                diagnostics[fileId] = AssetImportDiagnosticEntry(
                    assetPath = assetPath,
                    fileId = fileId,
                    displayName = displayName,
                    status = "in_progress",
                    rowCount = diagnostics[fileId]?.rowCount ?: 0,
                    errorMessage = null
                )
                scanner.publishDiagnostics(assetRoot, diagnostics.values)

                _progress.value = ImportProgress(
                    fileId = fileId,
                    fileName = displayName,
                    totalItems = files.size.toLong(),
                    importedItems = index.toLong(),
                    percent = ((index * 100f) / files.size.toFloat()).toInt().coerceIn(0, 100),
                    status = "in_progress",
                    message = "importing assets: $assetPath"
                )

                try {
                    context.assets.open(assetPath).use { input ->
                        importer.importFromJson(
                            inputStream = input,
                            batchSize = ImportDefaults.DEFAULT_BATCH_SIZE,
                            trace = null,
                            fallbackFileName = displayName,
                            fallbackFileId = fileId
                        ).collect { p ->
                            _progress.value = p.copy(fileId = fileId, fileName = displayName)
                        }
                    }
                    repo.markFileImported(fileId, displayName, System.currentTimeMillis(), "imported")
                    diagnostics[fileId] = AssetImportDiagnosticEntry(
                        assetPath = assetPath,
                        fileId = fileId,
                        displayName = displayName,
                        status = "imported",
                        rowCount = scanner.countImportedRows(assetPath, fileId),
                        errorMessage = null
                    )
                    scanner.publishDiagnostics(assetRoot, diagnostics.values)
                    observability.importCompleted(fileId, displayName, 0)
                } catch (t: Throwable) {
                    repo.markFileImported(fileId, displayName, System.currentTimeMillis(), "failed")
                    diagnostics[fileId] = AssetImportDiagnosticEntry(
                        assetPath = assetPath,
                        fileId = fileId,
                        displayName = displayName,
                        status = "failed",
                        rowCount = 0,
                        errorMessage = t.message
                    )
                    scanner.publishDiagnostics(assetRoot, diagnostics.values)
                    _progress.value = ImportProgress(
                        fileId = fileId,
                        fileName = displayName,
                        totalItems = files.size.toLong(),
                        importedItems = index.toLong(),
                        percent = ((index * 100f) / files.size.toFloat()).toInt().coerceIn(0, 100),
                        status = "failed",
                        message = t.message
                    )
                    observability.importFailed(fileId, displayName, t.message)
                }
            }
        }
    }

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
}
