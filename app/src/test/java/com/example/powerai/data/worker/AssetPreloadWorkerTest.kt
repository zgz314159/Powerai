package com.example.powerai.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.powerai.data.importer.AssetImportOutcome
import com.example.powerai.data.importer.DocumentImportManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The preload worker must not report success when a single built-in asset failed: a failed asset
 * (or an unexpected throw) yields [ListenableWorker.Result.retry], and a real cancellation keeps
 * propagating instead of being turned into a success.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class AssetPreloadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildWorker(manager: DocumentImportManager): AssetPreloadWorker =
        TestListenableWorkerBuilder<AssetPreloadWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = AssetPreloadWorker(appContext, workerParameters, manager)
                },
            )
            .build()

    @Test
    fun `returns success when every asset imported`() =
        runBlocking {
            val manager = mock<DocumentImportManager>()
            whenever(manager.importAssetsIfNeed(any())).thenReturn(AssetImportOutcome(scanned = 2, imported = 2))

            assertEquals(ListenableWorker.Result.success(), buildWorker(manager).doWork())
        }

    @Test
    fun `returns retry when a single asset failed`() =
        runBlocking {
            val manager = mock<DocumentImportManager>()
            whenever(manager.importAssetsIfNeed(any())).thenReturn(AssetImportOutcome(scanned = 2, imported = 1, failed = 1))

            val result = buildWorker(manager).doWork()

            assertEquals("a failed asset must not be reported as success", ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `returns retry when the import throws`() =
        runBlocking {
            val manager = mock<DocumentImportManager>()
            whenever(manager.importAssetsIfNeed(any())).thenAnswer { throw IOException("boom") }

            assertEquals(ListenableWorker.Result.retry(), buildWorker(manager).doWork())
        }

    @Test
    fun `propagates cancellation instead of retrying`() =
        runBlocking {
            val manager = mock<DocumentImportManager>()
            whenever(manager.importAssetsIfNeed(any())).thenAnswer { throw CancellationException("stopped") }

            var cancelled = false
            try {
                buildWorker(manager).doWork()
            } catch (c: CancellationException) {
                cancelled = true
            }

            assertTrue("cancellation must propagate from the worker", cancelled)
        }
}
