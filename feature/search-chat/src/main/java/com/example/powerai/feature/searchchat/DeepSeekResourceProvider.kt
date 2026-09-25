package com.example.powerai.feature.searchchat

import android.content.Context
import com.example.powerai.core.model.NativeResourceProvider
import java.io.File

internal class DeepSeekResourceProvider(
    private val context: Context
) : NativeResourceProvider {
    override fun getContext(): Any = context
    override fun getFilesDir(): String = context.filesDir.absolutePath
    override fun isGpuSupported(): Boolean = false
    override fun prepareModelFromAssets(assetName: String): String? = null
    override fun listAssets(path: String): List<String> = emptyList()
    override fun openAsset(path: String): String? = null
    override fun writeDiagnosticFile(fileName: String, content: String) {
        runCatching { File(context.filesDir, fileName).writeText(content) }
    }
    override fun appendDiagnosticFile(fileName: String, content: String) {
        runCatching { File(context.filesDir, fileName).appendText(content) }
    }
    override fun exportDiagnosticFile(sourceName: String, targetName: String) {}
}
