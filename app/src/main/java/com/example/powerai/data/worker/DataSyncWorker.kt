package com.example.powerai.data.worker

import com.example.powerai.core.data.dao.KnowledgeDao

import android.content.Context

// android.util.Log removed per TODO; use TraceLogger or other observability if needed
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.powerai.core.data.database.DatabaseMigrationUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: KnowledgeDao
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // log removed: doWork started
        return try {
            DatabaseMigrationUtils.fillMissingNormalizedData(dao)
            // log removed: fillMissingNormalizedData returned
            Result.success()
        } catch (t: Throwable) {
            // log removed: doWork failed
            Result.retry()
        }
    }
}
