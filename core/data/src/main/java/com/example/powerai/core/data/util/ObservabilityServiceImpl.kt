package com.example.powerai.core.data.util

import android.content.Context
import com.example.powerai.core.model.ObservabilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight observability implementation that records structured traces to `TraceLogger`. */
@Singleton
class ObservabilityServiceImpl @Inject constructor(@ApplicationContext private val context: Context) : ObservabilityService {
    private val tag = "PowerAi.Obs"

    override fun importStarted(fileId: String, fileName: String) {
        val msg = "import_started file=$fileId name=$fileName"
        TraceLogger.append(context, tag, msg)
    }

    override fun importProgress(fileId: String, fileName: String, importedItems: Long, totalItems: Long?) {
        val msg = "import_progress file=$fileId name=$fileName imported=$importedItems total=${totalItems ?: "?"}"
        TraceLogger.append(context, tag, msg)
    }

    override fun importCompleted(fileId: String, fileName: String, importedItems: Long) {
        val msg = "import_completed file=$fileId name=$fileName imported=$importedItems"
        TraceLogger.append(context, tag, msg)
    }

    override fun importFailed(fileId: String, fileName: String?, reason: String?) {
        val msg = "import_failed file=$fileId name=${fileName ?: "?"} reason=${reason ?: "unknown"}"
        TraceLogger.append(context, tag, msg)
    }

    override fun retrievalStarted(query: String) {
        val msg = "retrieve_started q=${query.take(200)}"
        TraceLogger.append(context, tag, msg)
    }

    override fun retrievalFinished(query: String, returned: Int, durationMs: Long) {
        val msg = "retrieve_finished q=${query.take(200)} returned=$returned took=${durationMs}ms"
        TraceLogger.append(context, tag, msg)
    }

    override fun aiCallStarted(endpoint: String, meta: String?) {
        val msg = "ai_call_started endpoint=$endpoint meta=${meta.orEmpty()}"
        TraceLogger.append(context, tag, msg)
    }

    override fun aiCallFinished(endpoint: String, durationMs: Long, success: Boolean, meta: String?) {
        val msg = "ai_call_finished endpoint=$endpoint success=$success took=${durationMs}ms meta=${meta.orEmpty()}"
        TraceLogger.append(context, tag, msg)
    }

    override fun logEvent(key: String, message: String) {
        val msg = "$key $message"
        TraceLogger.append(context, tag, msg)
    }
}
