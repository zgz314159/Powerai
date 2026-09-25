package com.example.powerai.core.model

/**
 * Interface to provide Android-specific resources to native libraries
 * without the domain layer depending on Android Context.
 */
interface NativeResourceProvider {
    /** Returns the underlying Android Context object. */
    fun getContext(): Any?

    /** Returns a directory path for native storage. */
    fun getFilesDir(): String?

    /** Returns true if the device supports GPU acceleration (e.g. Vulkan). */
    fun isGpuSupported(): Boolean

    /** Copies an asset to internal files directory and returns the absolute path. */
    fun prepareModelFromAssets(assetName: String): String?

    /** List assets under a specific path. */
    fun listAssets(path: String): List<String>

    /** Open an asset and return its content as string. */
    fun openAsset(path: String): String?

    /** Write text to a file in internal files directory for diagnostics. */
    fun writeDiagnosticFile(fileName: String, content: String)

    /** Append text to a file in internal files directory. */
    fun appendDiagnosticFile(fileName: String, content: String)

    /** Copy a diagnostic file from internal storage to external storage for collection. */
    fun exportDiagnosticFile(sourceName: String, targetName: String)
}
