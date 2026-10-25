package com.example.powerai.ui.screen.database

import com.example.powerai.data.importer.AssetImportDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

/** Characterization tests for [DatabaseViewModel] import diagnostics reload. */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelImportReloadTest {
    private val harnesses = mutableListOf<DatabaseViewModelTestHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.cancelCreatedViewModels() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    private fun newHarness(): DatabaseViewModelTestHarness = DatabaseViewModelTestHarness().also { harnesses += it }

    private fun completedImport(): AssetImportDiagnostics =
        AssetImportDiagnostics(scannedCount = 1, importedCount = 1, failedCount = 0, lastScanAt = 99L)

    @Test
    fun `completed import diagnostics auto reload after first load`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)
            viewModel.loadAll()
            advanceUntilIdle()
            val loadsAfterFirstLoad = harness.repo.loadCount

            harness.repo.allItems = listOf(knowledgeItem(2, "更新后条目", source = "操作规程B.docx"))
            harness.diagnosticsFlow.value = completedImport()
            advanceUntilIdle()

            assertEquals(loadsAfterFirstLoad + 1, harness.repo.loadCount)
            assertEquals(listOf("操作规程B.docx"), viewModel.uiState.value.groups.map { it.key })
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `import diagnostics completion before first load does not reload`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            harness.diagnosticsFlow.value = completedImport()
            val viewModel = harness.createViewModel(this)

            advanceUntilIdle()

            assertEquals(0, harness.repo.loadCount)
            assertTrue(viewModel.uiState.value.groups.isEmpty())
            assertTrue(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `import failure during empty load keeps state consistent`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            Mockito.doAnswer { throw IllegalStateException("import boom") }
                .whenever(harness.importer).importAssetsIfNeed()
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertTrue(state.groups.isEmpty())
            Mockito.verify(harness.importer, Mockito.times(1)).importAssetsIfNeed()
        }
}
