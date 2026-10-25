package com.example.powerai.data.util

import android.content.Context
import android.content.pm.PackageManager
import com.example.powerai.core.model.NativeResourceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNativeResourceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : NativeResourceProvider {
    override fun getContext(): Any = context

    override fun getFilesDir(): String = context.filesDir.absolutePath

    override fun isGpuSupported(): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature("android.hardware.vulkan.level") ||
               pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
    }

    override fun prepareModelFromAssets(assetName: String): String? {
        val assetPath = "models/$assetName"
        return try {
            context.assets.open(assetPath).use { input ->
                val outDir = java.io.File(context.filesDir, "models")
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = java.io.File(outDir, assetName)
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
                outFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun listAssets(path: String): List<String> {
        return context.assets.list(path)?.toList() ?: emptyList()
    }

    override fun openAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    override fun writeDiagnosticFile(fileName: String, content: String) {
        try {
            java.io.File(context.filesDir, fileName).writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    override fun appendDiagnosticFile(fileName: String, content: String) {
        try {
            java.io.File(context.filesDir, fileName).appendText(content, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    override fun exportDiagnosticFile(sourceName: String, targetName: String) {
        try {
            val src = java.io.File(context.filesDir, sourceName)
            val extDir = context.getExternalFilesDir(null)
            if (src.exists() && extDir != null) {
                val dst = java.io.File(extDir, targetName)
                src.inputStream().use { input ->
                    dst.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
