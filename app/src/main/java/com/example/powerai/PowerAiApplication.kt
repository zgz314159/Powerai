package com.example.powerai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.powerai.data.worker.AssetPreloadWorker
import com.example.powerai.data.worker.DataSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PowerAiApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var nativeVectorRepository: com.example.powerai.engine.nativecore.NativeVectorRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // IMPORTANT: WorkManager may be initialized by AndroidX Startup before Hilt injects workerFactory,
        // which would fall back to default configuration and break @HiltWorker creation.
        // We disable Startup initializer in manifest and initialize here with the injected factory.
        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (_: Throwable) {
        }

        // Asynchronously load existing native vector index via Hilt-injected repository.
        try {
            Thread {
                try {
                    val loaded = nativeVectorRepository.loadDefaultIndexIfExists()
                    // log removed: startup loadIndex -> $loaded
                } catch (e: Throwable) {
                    // log removed: startup load failed
                }
            }.start()
        } catch (_: Throwable) {
        }

        // Install global crash handler so we can capture stack traces to disk for
        // further post‑mortem analysis. This complements the ViewModel-level
        // logging we already perform elsewhere.
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                filesDir.resolve("rag_crash.log")
                    .appendText(" uncaught:${thread.name}: ${ex.stackTraceToString()}\n")
            } catch (_: Throwable) {}
            // let Android proceed with default kill after logging
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(2)
        }

        // Kick off asset KB import once. KEEP to avoid duplicate concurrent imports.
        try {
            WorkManager.getInstance(this)
                .enqueueUniqueWork(
                    "asset_preload",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<AssetPreloadWorker>().build()
                )

            // Normalize/search-index maintenance.
            WorkManager.getInstance(this)
                .enqueueUniqueWork(
                    "data_sync",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<DataSyncWorker>().build()
                )
        } catch (_: Throwable) {
        }
    }
}
