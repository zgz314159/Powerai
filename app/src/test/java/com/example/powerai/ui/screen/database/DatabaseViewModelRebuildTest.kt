package com.example.powerai.ui.screen.database

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

/**
 * The database tab's import maintenance must be honest: "rescan" truly retries the asset import,
 * and a rebuild only deletes after the user confirms — requesting it must not touch the data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelRebuildTest {
    private val harnesses = mutableListOf<DatabaseViewModelTestHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.cancelCreatedViewModels() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    private fun newHarness(): DatabaseViewModelTestHarness = DatabaseViewModelTestHarness().also { harnesses += it }

    @Test
    fun `request rebuild only asks for confirmation and deletes nothing`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RequestRebuildKnowledgeBase)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRebuildConfirmVisible)
            Mockito.verify(harness.importer, Mockito.never()).rebuildBuiltInKnowledgeBase()
        }

    @Test
    fun `cancel rebuild hides confirmation without deleting`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RequestRebuildKnowledgeBase)
            viewModel.onIntent(DatabaseIntent.CancelRebuildKnowledgeBase)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRebuildConfirmVisible)
            Mockito.verify(harness.importer, Mockito.never()).rebuildBuiltInKnowledgeBase()
        }

    @Test
    fun `confirm rebuild runs the rebuild`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RequestRebuildKnowledgeBase)
            viewModel.onIntent(DatabaseIntent.ConfirmRebuildKnowledgeBase)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRebuildConfirmVisible)
            Mockito.verify(harness.importer, Mockito.times(1)).rebuildBuiltInKnowledgeBase()
        }

    @Test
    fun `retry intent truly reruns the asset import`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            // Non-empty content so the post-retry reload does not itself trigger an import.
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RetryAssetImport)
            advanceUntilIdle()

            Mockito.verify(harness.importer, Mockito.times(1)).importAssetsIfNeed()
        }

    @Test
    fun `refresh diagnostics intent also reruns the asset import`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RefreshImportDiagnostics)
            advanceUntilIdle()

            Mockito.verify(harness.importer, Mockito.times(1)).importAssetsIfNeed()
        }

    @Test
    fun `clear all needs the scope and final confirmation before deleting`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RequestClearAllKnowledgeBase)
            advanceUntilIdle()
            assertEquals(ClearAllConfirmStep.SCOPE, viewModel.uiState.value.clearAllStep)
            Mockito.verify(harness.importer, Mockito.never()).clearAllKnowledgeBases()

            viewModel.onIntent(DatabaseIntent.ContinueClearAllKnowledgeBase)
            advanceUntilIdle()
            assertEquals(ClearAllConfirmStep.FINAL, viewModel.uiState.value.clearAllStep)
            Mockito.verify(harness.importer, Mockito.never()).clearAllKnowledgeBases()

            viewModel.onIntent(DatabaseIntent.ConfirmClearAllKnowledgeBase)
            advanceUntilIdle()
            assertEquals(ClearAllConfirmStep.NONE, viewModel.uiState.value.clearAllStep)
            Mockito.verify(harness.importer, Mockito.times(1)).clearAllKnowledgeBases()
        }

    @Test
    fun `cancel clear all deletes nothing`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RequestClearAllKnowledgeBase)
            viewModel.onIntent(DatabaseIntent.CancelClearAllKnowledgeBase)
            advanceUntilIdle()

            assertEquals(ClearAllConfirmStep.NONE, viewModel.uiState.value.clearAllStep)
            Mockito.verify(harness.importer, Mockito.never()).clearAllKnowledgeBases()
        }

    @Test
    fun `remove user package only runs after confirmation`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            Mockito.`when`(harness.userKbImporter.removePackage("user:x"))
                .thenReturn(com.example.powerai.data.importer.UserKbPackageRemovalResult.Removed(3))
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.onIntent(DatabaseIntent.RequestRemoveUserPackage("user:x"))
            advanceUntilIdle()
            assertEquals("user:x", viewModel.uiState.value.pendingRemovePackageId)
            Mockito.verify(harness.userKbImporter, Mockito.never()).removePackage(Mockito.anyString())

            viewModel.onIntent(DatabaseIntent.ConfirmRemoveUserPackage)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.pendingRemovePackageId)
            Mockito.verify(harness.userKbImporter, Mockito.times(1)).removePackage("user:x")
        }
}
