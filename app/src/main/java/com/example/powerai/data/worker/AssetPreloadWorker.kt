package com.example.powerai.data.worker

import android.content.Context

// android.util.Log removed per TODO order; TraceLogger remains for diagnostics
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.core.data.util.TraceLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.lang.StringBuilder

@HiltWorker
class AssetPreloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importManager: DocumentImportManager
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // log removed: doWork started
        try { TraceLogger.append(applicationContext, "AssetPreloadWorker", "doWork started") } catch (_: Throwable) {}
        return try {
            val outcome = importManager.importAssetsIfNeed()
            // log removed: importAssetsIfNeed returned
            runCatching {
                TraceLogger.append(
                    applicationContext,
                    "AssetPreloadWorker",
                    "importAssetsIfNeed returned imported=${outcome.imported} failed=${outcome.failed}",
                )
            }
            // A single failed asset must not be reported as an overall success; retry later.
            if (outcome.hasFailures) Result.retry() else Result.success()
        } catch (c: CancellationException) {
            // The worker was stopped; let WorkManager observe the cancellation.
            throw c
        } catch (t: Throwable) {
            // log removed: doWork failed
            try {
                val sb = StringBuilder()
                sb.append("doWork failed: ")
                sb.append(t.message)
                sb.append("\n")
                sb.append(t.stackTraceToString().take(2000))
                TraceLogger.append(applicationContext, "AssetPreloadWorker", sb.toString())
            } catch (_: Throwable) {}
            Result.retry()
        }
    }
}
