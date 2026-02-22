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
    lateinit var nativeVectorRepository: com.example.powerai.data.retriever.NativeVectorRepository

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
                    android.util.Log.i("NativeVectorRepo", "startup loadIndex -> $loaded")
                } catch (e: Throwable) {
                    android.util.Log.e("NativeVectorRepo", "startup load failed", e)
                }
            }.start()
        } catch (_: Throwable) {
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
