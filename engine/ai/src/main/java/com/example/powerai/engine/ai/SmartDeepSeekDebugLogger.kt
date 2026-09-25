package com.example.powerai.engine.ai

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmartDeepSeekDebugLogger {
    private const val LOG_FILE_NAME = "smart_deepseek_debug.log"
    private const val AB_RUNS_FILE_NAME = "smart_ab_runs.jsonl"
    private const val CRASH_FILE_NAME = "rag_crash.log"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    fun resetSession(
        provider: NativeResourceProvider?,
        sessionId: String,
        query: String,
        answerMode: String,
        backendMode: String,
        threadMode: String
    ) {
        if (provider == null) return
        synchronized(lock) {
            val logMsg = buildString {
                appendLine()
                appendLine("=== SMART DEBUG SESSION START ===")
                appendLine("time=${timestamp()}")
                appendLine("sessionId=$sessionId")
                appendLine("query=$query")
                appendLine("answerMode=$answerMode")
                appendLine("backendMode=$backendMode")
                appendLine("threadMode=$threadMode")
            }
            provider.appendDiagnosticFile(LOG_FILE_NAME, logMsg)
            provider.writeDiagnosticFile(CRASH_FILE_NAME, "")
        }
    }

    fun logEvent(provider: NativeResourceProvider?, tag: String, message: String) {
        if (provider == null) return
        synchronized(lock) {
            provider.appendDiagnosticFile(LOG_FILE_NAME, "[${timestamp()}] [$tag] $message\n")
        }
    }

    fun logChunk(
        provider: NativeResourceProvider?,
        index: Int,
        rawChunk: String,
        visibleChunk: String,
        inThinkBlock: Boolean,
        pendingTagFragment: String,
        currentResultLength: Int
    ) {
        val rawCompact = compact(rawChunk)
        val visibleCompact = compact(visibleChunk)
        logEvent(
            provider = provider,
            tag = "CHUNK",
            message = "#${index} raw='${rawCompact}' visible='${visibleCompact}' inThink=$inThinkBlock pendingTag='${compact(pendingTagFragment)}' resultLen=$currentResultLength"
        )
    }

    fun logSummary(
        provider: NativeResourceProvider?,
        rawOutputLength: Int,
        finalAnswerLength: Int,
        streamedChunkCount: Int,
        firstTokenLatencyMs: Long?,
        decodeCharsPerSecond: Double?
    ) {
        logEvent(
            provider = provider,
            tag = "SUMMARY",
            message = "rawOutputLength=$rawOutputLength finalAnswerLength=$finalAnswerLength streamedChunks=$streamedChunkCount firstTokenLatencyMs=${firstTokenLatencyMs ?: -1} decodeCharsPerSecond=${decodeCharsPerSecond ?: -1.0}"
        )
    }

    fun appendBenchmarkRecord(provider: NativeResourceProvider?, record: SmartAbRunRecord) {
        if (provider == null) return
        synchronized(lock) {
            provider.appendDiagnosticFile(AB_RUNS_FILE_NAME, benchmarkJson(record).toString() + "\n")
        }
    }

    private fun benchmarkJson(record: SmartAbRunRecord): JSONObject {
        return JSONObject().apply {
            put("sessionId", record.sessionId)
            put("status", record.status)
            put("query", record.query)
            put("answerMode", record.answerMode)
            put("backendMode", record.backendMode)
            put("threadMode", record.threadMode)
            put("batchMode", record.batchMode)
            put("batchThreadMode", record.batchThreadMode)
            put("startedAtEpochMs", record.startedAtEpochMs)
            put("finishedAtEpochMs", record.finishedAtEpochMs)
            putNullable("retrievalMs", record.retrievalMs)
            putNullable("promptBuildMs", record.promptBuildMs)
            putNullable("firstTokenLatencyMs", record.firstTokenLatencyMs)
            putNullable("answerStartLatencyMs", record.answerStartLatencyMs)
            putNullable("decodeCharsPerSecond", record.decodeCharsPerSecond)
            putNullable("streamedChunkCount", record.streamedChunkCount)
            putNullable("rawOutputLength", record.rawOutputLength)
            putNullable("finalAnswerLength", record.finalAnswerLength)
            put("evidenceCount", record.evidenceCount)
            put("promptChars", record.promptChars)
            putNullable("errorMessage", record.errorMessage)
            put("prefixReuseEnabled", record.prefixReuseEnabled)
            put("prefixReuseHit", record.prefixReuseHit)
            put("stateSnapshotReuseEnabled", record.stateSnapshotReuseEnabled)
            put("stateSnapshotReuseHit", record.stateSnapshotReuseHit)
            put("stateSnapshotBytes", record.stateSnapshotBytes)
            put("cachePromptEnabled", record.cachePromptEnabled)
            put("cacheReuseMinTokens", record.cacheReuseMinTokens)
            putNullable("benchmarkKind", record.benchmarkKind)
            putNullable("benchmarkGroupId", record.benchmarkGroupId)
            putNullable("benchmarkOrder", record.benchmarkOrder)
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun compact(value: String): String {
        return value
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace(Regex("\\s+"), " ")
            .take(120)
    }

    private fun timestamp(): String = timeFormat.format(Date())
}
