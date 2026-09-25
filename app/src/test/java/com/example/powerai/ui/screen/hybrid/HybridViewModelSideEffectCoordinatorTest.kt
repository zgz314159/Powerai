package com.example.powerai.ui.screen.hybrid

import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.domain.usecase.HybridHistoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelSideEffectCoordinatorTest {

    @Test
    fun `observeImportProgress forwards importer progress values`() = runTest {
        val historyUseCase = Mockito.mock(HybridHistoryUseCase::class.java)
        whenever(historyUseCase.localHistory).thenReturn(MutableStateFlow(emptyList()))
        whenever(historyUseCase.smartHistory).thenReturn(MutableStateFlow(emptyList()))
        val importer = Mockito.mock(DocumentImportManager::class.java)
        val progressFlow = MutableStateFlow<ImportProgress?>(null)
        whenever(importer.progress).thenReturn(progressFlow)
        val coordinator = HybridViewModelSideEffectCoordinator(
            historyUseCase = historyUseCase,
            importer = importer,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        var observed: ImportProgress? = null
        val observationJob = coordinator.observeImportProgress(this) { observed = it }

        val progress = ImportProgress(
            fileId = "f1",
            fileName = "demo",
            totalItems = 10,
            importedItems = 2,
            percent = 20,
            status = "in_progress"
        )
        progressFlow.value = progress
        advanceUntilIdle()

        assertEquals(progress, observed)
        observationJob.cancel()
    }

    @Test
    fun `initialize runs history init and asset import`() = runTest {
        val historyUseCase = Mockito.mock(HybridHistoryUseCase::class.java)
        whenever(historyUseCase.localHistory).thenReturn(MutableStateFlow(emptyList()))
        whenever(historyUseCase.smartHistory).thenReturn(MutableStateFlow(emptyList()))
        val importer = Mockito.mock(DocumentImportManager::class.java)
        whenever(importer.progress).thenReturn(MutableStateFlow(null))
        val coordinator = HybridViewModelSideEffectCoordinator(
            historyUseCase = historyUseCase,
            importer = importer,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        coordinator.initialize(this)
        advanceUntilIdle()

        verify(historyUseCase).init()
        verify(importer).importAssetsIfNeed()
    }

    @Test
    fun `history mutations delegate to use case`() = runTest {
        val historyUseCase = Mockito.mock(HybridHistoryUseCase::class.java)
        whenever(historyUseCase.localHistory).thenReturn(MutableStateFlow(emptyList()))
        whenever(historyUseCase.smartHistory).thenReturn(MutableStateFlow(emptyList()))
        val importer = Mockito.mock(DocumentImportManager::class.java)
        whenever(importer.progress).thenReturn(MutableStateFlow(null))
        val coordinator = HybridViewModelSideEffectCoordinator(
            historyUseCase = historyUseCase,
            importer = importer,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        coordinator.addLocalQuery(this, "local")
        coordinator.addSmartQuery(this, "smart")
        coordinator.clearLocalHistory(this)
        coordinator.clearSmartHistory(this)
        advanceUntilIdle()

        verify(historyUseCase).addLocal("local")
        verify(historyUseCase).addSmart("smart")
        verify(historyUseCase).clearLocal()
        verify(historyUseCase).clearSmart()
    }
}