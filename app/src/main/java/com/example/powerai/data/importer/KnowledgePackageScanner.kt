package com.example.powerai.data.importer

import android.content.Context
import com.example.powerai.AppConfig
import com.example.powerai.core.model.KbManifest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner that looks for knowledge packages in both bundled assets and external storage.
 */
@Singleton
class KnowledgePackageScanner @Inject constructor(
    private val context: Context,
    private val assetScanner: AssetImportScanner,
    private val gson: Gson
) {
    /**
     * Lists all JSON knowledge base files from assets and external storage.
     * Returns a list of [KnowledgeSource].
     */
    suspend fun listAllKnowledgeSources(): List<KnowledgeSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<KnowledgeSource>()

        // 1. Scan bundled assets
        assetScanner.listAssetFilesRecursive(AppConfig.BUNDLED_KB_ROOT)
            .filter { assetScanner.shouldImportAssetJson(it) }
            .forEach { assetPath ->
                sources.add(KnowledgeSource.Asset(assetPath, assetScanner.assetDisplayName(assetPath)))
            }

        // 2. Scan external storage
        val externalDir = File(context.filesDir, AppConfig.EXTERNAL_KB_DIR)
        if (externalDir.exists() && externalDir.isDirectory) {
            // Group by directory to look for manifests
            externalDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val manifestFile = File(dir, "KB_MANIFEST.json")
                if (manifestFile.exists()) {
                    runCatching {
                        val manifest = gson.fromJson(manifestFile.readText(), KbManifest::class.java)
                        // Look for the main JSON file defined in manifest or just anything named knowledge_base
                        val kbFile = dir.walkTopDown().find { it.name.contains("knowledge_base") && it.extension == "json" }
                        if (kbFile != null) {
                            sources.add(KnowledgeSource.External(
                                kbFile,
                                manifest.displayName,
                                version = manifest.version
                            ))
                        }
                    }
                } else {
                    // Fallback to legacy scanning
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension.lowercase() == "json" }
                        .filter { it.name.contains("knowledge_base") }
                        .forEach { file ->
                            sources.add(KnowledgeSource.External(file, file.parentFile?.name ?: file.name))
                        }
                }
            }

            // Also scan root of externalDir for loose files
            externalDir.listFiles()?.filter { it.isFile && it.extension == "json" && it.name.contains("knowledge_base") }?.forEach { file ->
                sources.add(KnowledgeSource.External(file, file.name))
            }
        }

        sources.distinctBy { it.identifier }
    }
}

sealed class KnowledgeSource {
    abstract val displayName: String
    abstract val identifier: String
    abstract val version: String?

    data class Asset(
        val assetPath: String,
        override val displayName: String,
        override val version: String? = null
    ) : KnowledgeSource() {
        override val identifier: String = "asset:$assetPath"
    }

    data class External(
        val file: File,
        override val displayName: String,
        override val version: String? = null
    ) : KnowledgeSource() {
        override val identifier: String = "file:${file.absolutePath}"
    }
}
