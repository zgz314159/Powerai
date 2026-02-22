package com.example.powerai.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.database.DatabaseMigrationUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: KnowledgeDao
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.i("DataSyncWorker", "doWork started")
        return try {
            DatabaseMigrationUtils.fillMissingNormalizedData(dao)
            Log.i("DataSyncWorker", "fillMissingNormalizedData returned")
            Result.success()
        } catch (t: Throwable) {
            Log.e("DataSyncWorker", "doWork failed", t)
            Result.retry()
        }
    }
}
