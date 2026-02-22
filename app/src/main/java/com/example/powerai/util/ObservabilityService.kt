package com.example.powerai.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight observability wrapper that records structured traces to `TraceLogger` and Logcat. */
@Singleton
class ObservabilityService @Inject constructor(@ApplicationContext private val context: Context) {
    private val tag = "PowerAi.Obs"

    fun importStarted(fileId: String, fileName: String) {
        val msg = "import_started file=$fileId name=$fileName"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun importProgress(fileId: String, fileName: String, importedItems: Long, totalItems: Long?) {
        val msg = "import_progress file=$fileId name=$fileName imported=$importedItems total=${totalItems ?: "?"}"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun importCompleted(fileId: String, fileName: String, importedItems: Long) {
        val msg = "import_completed file=$fileId name=$fileName imported=$importedItems"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun importFailed(fileId: String, fileName: String?, reason: String?) {
        val msg = "import_failed file=$fileId name=${fileName ?: "?"} reason=${reason ?: "unknown"}"
        Log.w(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun retrievalStarted(query: String) {
        val msg = "retrieve_started q=${query.take(200)}"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun retrievalFinished(query: String, returned: Int, durationMs: Long) {
        val msg = "retrieve_finished q=${query.take(200)} returned=$returned took=${durationMs}ms"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun aiCallStarted(endpoint: String, meta: String? = null) {
        val msg = "ai_call_started endpoint=$endpoint meta=${meta.orEmpty()}"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun aiCallFinished(endpoint: String, durationMs: Long, success: Boolean, meta: String? = null) {
        val msg = "ai_call_finished endpoint=$endpoint success=$success took=${durationMs}ms meta=${meta.orEmpty()}"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }

    fun logEvent(key: String, message: String) {
        val msg = "$key $message"
        Log.d(tag, msg)
        TraceLogger.append(context, tag, msg)
    }
}
