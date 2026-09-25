package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.core.model.SmartThreadBenchmarkResult
import com.example.powerai.core.model.SmartThreadPreset
import android.content.Context
import com.example.powerai.engine.ai.SmartDeepSeekDebugLogger
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.PowerAIEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class DeepSeekStateSnapshotHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeResourceProvider: NativeResourceProvider,
    private val sessionManager: DeepSeekSessionManager
) {
    private val engine: PowerAIEngine? get() = sessionManager.engine
    private data class CachedPromptState(
        val key: String,
        val stateData: ByteArray,
        val capturedAtEpochMs: Long
    )

    private var cachedPromptState: CachedPromptState? = null
    var stateSnapshotReuseEnabled: Boolean = false

    fun clearCache() {
        cachedPromptState = null
    }

    fun buildStateSnapshotKey(
        promptPrefixKey: String,
        backendMode: SmartBackendMode,
        threadPreset: SmartThreadPreset,
        batchSizePreset: SmartBatchSizePreset,
        batchThreadPreset: SmartBatchThreadPreset,
        prefixReuseEnabled: Boolean
    ): String {
        return listOf(
            promptPrefixKey,
            backendMode.name,
            threadPreset.threadCount.toString(),
            batchSizePreset.tokenCount.toString(),
            batchThreadPreset.threadCount.toString(),
            prefixReuseEnabled.toString()
        ).joinToString("|")
    }

    suspend fun prepareExplicitStateSnapshot(snapshotKey: String): Pair<Boolean, Int> {
        if (!stateSnapshotReuseEnabled) return false to 0
        val cachedState = cachedPromptState
        if (cachedState != null && cachedState.key == snapshotKey && cachedState.stateData.isNotEmpty()) {
            val restored = withContext(Dispatchers.IO) {
                engine?.loadStateData(cachedState.stateData) ?: false
            }
            SmartDeepSeekDebugLogger.logEvent(
                provider = nativeResourceProvider,
                tag = "STATE_SNAPSHOT",
                message = "restore key=$snapshotKey restored=$restored bytes=${cachedState.stateData.size} ageMs=${System.currentTimeMillis() - cachedState.capturedAtEpochMs}"
            )
            if (restored) return true to cachedState.stateData.size
        }

        val snapshotBytes = withContext(Dispatchers.IO) {
            engine?.getStateData()
        }
        val byteCount = snapshotBytes?.size ?: 0
        if (snapshotBytes != null && snapshotBytes.isNotEmpty()) {
            cachedPromptState = CachedPromptState(
                key = snapshotKey,
                stateData = snapshotBytes,
                capturedAtEpochMs = System.currentTimeMillis()
            )
            SmartDeepSeekDebugLogger.logEvent(
                provider = nativeResourceProvider,
                tag = "STATE_SNAPSHOT",
                message = "capture key=$snapshotKey bytes=$byteCount"
            )
        } else {
            SmartDeepSeekDebugLogger.logEvent(
                provider = nativeResourceProvider,
                tag = "STATE_SNAPSHOT",
                message = "capture key=$snapshotKey failed"
            )
        }
        return false to byteCount
    }

    suspend fun runProbe(): Pair<Boolean, Int> {
        val stateData = withContext(Dispatchers.IO) {
            engine?.getStateData()
        }
        if (stateData == null || stateData.isEmpty()) {
            return false to 0
        }
        val reloaded = withContext(Dispatchers.IO) {
            engine?.loadStateData(stateData) ?: false
        }
        SmartDeepSeekDebugLogger.logEvent(
            provider = nativeResourceProvider,
            tag = "STATE_SNAPSHOT",
            message = "probe bytes=${stateData.size} reloaded=$reloaded"
        )
        return reloaded to stateData.size
    }
}
