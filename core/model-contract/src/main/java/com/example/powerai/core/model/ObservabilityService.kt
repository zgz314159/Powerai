package com.example.powerai.core.model

/**
 * Interface for logging and observability without depending on Android specific classes.
 */
interface ObservabilityService {
    fun importStarted(fileId: String, fileName: String)
    fun importProgress(fileId: String, fileName: String, importedItems: Long, totalItems: Long?)
    fun importCompleted(fileId: String, fileName: String, importedItems: Long)
    fun importFailed(fileId: String, fileName: String?, reason: String?)
    fun retrievalStarted(query: String)
    fun retrievalFinished(query: String, returned: Int, durationMs: Long)
    fun aiCallStarted(endpoint: String, meta: String? = null)
    fun aiCallFinished(endpoint: String, durationMs: Long, success: Boolean, meta: String? = null)
    fun logEvent(key: String, message: String)
}
