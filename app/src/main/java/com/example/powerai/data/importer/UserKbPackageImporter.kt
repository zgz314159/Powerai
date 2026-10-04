package com.example.powerai.data.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.powerai.AppConfig
import com.example.powerai.core.data.dao.EmbeddingDao
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.importer.ImportDefaults
import com.example.powerai.core.data.importer.ImportUtils
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import com.example.powerai.core.model.KnowledgePackages
import com.example.powerai.core.repository.VectorRepository
import com.example.powerai.util.PdfSourceRef
import com.example.powerai.util.PdfStorage
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Imports a knowledge base that the user produced with PaddleModels and copied into a directory on
 * the device, via the system directory picker.
 *
 * Contract: the picked directory contains the PaddleModels `knowledge_base.json` and every resource
 * it references (the PNG files under `shots/`). The KB JSON is fed unchanged to the existing
 * [StreamingJsonResourceImporter]; only image references are rewritten to point at an app-private
 * copy, so blocks keep rendering after the source directory is removed.
 *
 * Package isolation: the package id is derived from the picked directory itself
 * (`user:{sha256(directory identity)}`), never from the PDF sha or the declared `source`. Re-importing
 * byte-identical content is skipped; changed content replaces **only** this package's rows and
 * resources; built-in assets, other user packages and manual imports are untouched.
 *
 * Atomicity: resources are copied into a brand-new content-addressed directory before the row is
 * committed. A failure or cancellation rolls the transaction back and removes the staged directory,
 * so no searchable half-package and no row pointing at a missing image is ever left behind; the
 * previous version's rows and files survive until the new version has committed.
 */
@Singleton
class UserKbPackageImporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: KnowledgeDao,
        private val visionCacheDao: VisionCacheDao? = null,
        private val embeddingDao: EmbeddingDao? = null,
        private val vectorRepository: VectorRepository? = null,
        @Named("vector_index_path") private val vectorIndexPath: String = "vector_index.bin",
    ) {
        private val _packages = MutableStateFlow<List<UserKbPackageSummary>>(emptyList())

        /** Observable list of the user-imported packages, refreshed after every import/removal. */
        val packages: StateFlow<List<UserKbPackageSummary>> = _packages

        /** Re-read the user packages from the database and publish the management list. */
        suspend fun refreshPackages() {
            _packages.value = listPackages()
        }

        /** User-imported packages (`user:` ids), newest first, with entry count and PDF association. */
        suspend fun listPackages(): List<UserKbPackageSummary> =
            dao.getImportedFiles()
                .filter { KnowledgePackages.isUserPackage(it.fileId) }
                .sortedByDescending { it.timestamp }
                .map { marker ->
                    val ref = PdfSourceRef.parse(runCatching { dao.getFirstByPackageId(marker.fileId)?.source }.getOrNull())
                    UserKbPackageSummary(
                        packageId = marker.fileId,
                        displayName = marker.fileName.ifBlank { marker.fileId },
                        entries = dao.countByPackageId(marker.fileId),
                        updatedAtMs = marker.timestamp,
                        status = marker.status,
                        pdfFileName = ref?.fileName,
                        pdfAssociated = ref != null && PdfStorage.hasPdf(context, ref.fileId),
                    )
                }

        /**
         * Remove exactly one user package: its rows, FTS entries, id-keyed caches and marker in one
         * transaction, then — only after the commit — its private mirror directory and the derived
         * native vector index. Refuses anything that is not a `user:` package, so built-in packages,
         * other user packages, manual imports and shared PDFs are never touched. A failure leaves the
         * package fully intact and is reported as such (never a false success).
         */
        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        suspend fun removePackage(packageId: String): UserKbPackageRemovalResult {
            if (!KnowledgePackages.isUserPackage(packageId)) {
                return UserKbPackageRemovalResult.Failed("仅支持移除用户导入的知识库包")
            }
            if (runCatching { dao.getImportedFile(packageId) }.getOrNull() == null) {
                return UserKbPackageRemovalResult.Failed("未找到该用户知识库包")
            }
            val entries = runCatching { dao.countByPackageId(packageId) }.getOrDefault(0)

            var ownedIds: List<Long> = emptyList()
            try {
                dao.runInTransaction {
                    ownedIds = dao.getIdsByPackageId(packageId)
                    dao.deleteByPackageId(packageId)
                    dao.deleteImportedFile(packageId)
                    dao.rebuildFts()
                    invalidateEntityCachesFor(visionCacheDao, embeddingDao, ownedIds)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                return UserKbPackageRemovalResult.Failed(error.message ?: "移除失败")
            }

            // After the commit: drop only this package's private mirror, then invalidate the derived
            // index (a stale id must not be returned in the same process).
            val mirrorDir = packageDirFor(packageId)
            val mirrorRemoved = runCatching { mirrorDir.deleteRecursively() }.getOrDefault(false) || !mirrorDir.exists()
            val indexOk = invalidateNativeVectorIndex(context, vectorRepository, vectorIndexPath)
            refreshPackages()
            return if (mirrorRemoved && indexOk) {
                UserKbPackageRemovalResult.Removed(entries)
            } else {
                UserKbPackageRemovalResult.Failed("已从数据库移除，但私有资源清理失败，请重试")
            }
        }

        /** Import the directory behind a SAF tree uri obtained from [android.provider.DocumentsContract]. */
        suspend fun importDirectory(
            treeUri: Uri,
            onProgress: (UserKbImportProgress) -> Unit = {},
        ): UserKbImportResult {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            return importFromSource(SafKbDirectorySource(context, treeUri), onProgress)
        }

        /** Source-level entry point; used by [importDirectory] and directly by tests. */
        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        suspend fun importFromSource(
            source: KbDirectorySource,
            onProgress: (UserKbImportProgress) -> Unit = {},
        ): UserKbImportResult {
            val bytes =
                runCatching { source.open(KB_JSON_FILE_NAME)?.use { it.readBytes() } }.getOrNull()
                    ?: return UserKbImportResult.Failed("所选目录中未找到 knowledge_base.json，请选择包含该文件的输出目录")

            val contentSha = ImportUtils.sha256Hex(bytes)
            val packageId = KnowledgePackages.USER_PREFIX + ImportUtils.sha256Hex(source.identity)

            val existing = runCatching { dao.getImportedFile(packageId) }.getOrNull()
            if (existing?.status?.trim()?.lowercase() == AssetImportStatus.IMPORTED &&
                existing.contentSha256 == contentSha
            ) {
                refreshPackages()
                return UserKbImportResult.Skipped(
                    entries = dao.countByPackageId(packageId),
                    displayName = source.displayName,
                    pdf = pdfPromptFor(packageId),
                )
            }

            val packageDir = packageDirFor(packageId)
            val versionDir = File(packageDir, contentSha)
            versionDir.deleteRecursively()
            versionDir.mkdirs()
            val materializer = UserKbAssetMaterializer(source, versionDir)

            try {
                dao.runInTransaction {
                    dao.deleteByPackageId(packageId)
                    StreamingJsonResourceImporter(dao, imageUriRewriter = materializer::materialize)
                        .importFromJson(
                            inputStream = ByteArrayInputStream(bytes),
                            batchSize = ImportDefaults.DEFAULT_BATCH_SIZE,
                            trace = null,
                            fallbackFileName = source.displayName,
                            fallbackFileId = packageId,
                        )
                        .collect { progress -> onProgress(UserKbImportProgress(source.displayName, progress.importedItems)) }
                    dao.insertImportedFile(
                        ImportedFileEntity(
                            fileId = packageId,
                            fileName = source.displayName,
                            timestamp = System.currentTimeMillis(),
                            status = AssetImportStatus.IMPORTED,
                            contentSha256 = contentSha,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                versionDir.deleteRecursively()
                throw cancellation
            } catch (error: Throwable) {
                versionDir.deleteRecursively()
                return UserKbImportResult.Failed(error.message ?: "导入失败")
            }

            pruneOldVersions(packageDir, versionDir)
            refreshPackages()
            return UserKbImportResult.Imported(
                entries = dao.countByPackageId(packageId),
                blocks = runCatching { countBlocks(String(bytes, Charsets.UTF_8)) }.getOrDefault(0),
                assets = materializer.copiedCount,
                displayName = source.displayName,
                pdf = pdfPromptFor(packageId),
            )
        }

        private fun packageDirFor(packageId: String): File =
            File(File(context.filesDir, AppConfig.USER_KB_MIRROR_DIR), ImportUtils.sha256Hex(packageId))

        /** After a committed replace, drop the previous content versions of this package only. */
        private fun pruneOldVersions(
            packageDir: File,
            keep: File,
        ) {
            packageDir.listFiles()?.forEach { child ->
                if (child.name != keep.name) child.deleteRecursively()
            }
        }

        private suspend fun pdfPromptFor(packageId: String): PdfPromptInfo? {
            val declaredSource = runCatching { dao.getFirstByPackageId(packageId)?.source }.getOrNull()
            val ref = PdfSourceRef.parse(declaredSource) ?: return null
            return if (PdfStorage.hasPdf(context, ref.fileId)) {
                null
            } else {
                PdfPromptInfo(fileId = ref.fileId, fileName = ref.fileName)
            }
        }

        private fun countBlocks(json: String): Int =
            runCatching {
                val root = JsonParser().parse(json)
                val entries =
                    if (root.isJsonObject) {
                        root.asJsonObject.getAsJsonArray("entries")
                    } else {
                        root.takeIf { it.isJsonArray }?.asJsonArray
                    }
                (entries ?: return@runCatching 0).sumOf { element ->
                    element.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("blocks")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?.size()
                        ?: 0
                }
            }.getOrDefault(0)
    }
