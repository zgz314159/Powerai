package com.example.powerai.ui.screen.database

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
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

/**
 * Characterization tests for [DatabaseViewModel] top-level content job
 * coordination: the load-once guard, lifecycle cancellation and
 * newest-request-wins cancellation of in-flight loads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelContentJobTest {
    private val harnesses = mutableListOf<DatabaseViewModelTestHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.cancelCreatedViewModels() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    private fun newHarness(): DatabaseViewModelTestHarness = DatabaseViewModelTestHarness().also { harnesses += it }

    @Test
    fun `ensureLoaded loads only once`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)

            viewModel.ensureLoaded()
            advanceUntilIdle()
            viewModel.ensureLoaded()
            advanceUntilIdle()

            assertEquals(1, harness.repo.loadCount)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `lifecycle cancellation stops in-flight load without further interaction`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.loadGate = CompletableDeferred()
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()
            assertEquals(1, harness.repo.loadCount)
            assertTrue(viewModel.uiState.value.isLoading)

            viewModel.viewModelScope.coroutineContext.job.cancel()
            harness.repo.loadGate!!.complete(Unit)
            advanceUntilIdle()

            assertTrue(harness.repo.loadWasCancelled)
            assertEquals(1, harness.repo.loadCount)
            assertTrue(viewModel.uiState.value.groups.isEmpty())
        }

    @Test
    fun `new content request cancels the in-flight one`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val gate = CompletableDeferred<Unit>()
            harness.repo.loadGate = gate
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            harness.repo.searchItems = {
                listOf(knowledgeItem(3, "新查询条目", source = "操作规程B.docx"))
            }
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()
            assertEquals(1, harness.repo.loadCount)
            assertTrue(viewModel.uiState.value.isLoading)

            harness.repo.loadGate = null
            viewModel.search("beta")
            advanceUntilIdle()

            assertTrue(harness.repo.loadWasCancelled)
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals("beta", state.currentQuery)
            assertEquals(listOf("操作规程B.docx"), state.groups.map { it.key })

            gate.complete(Unit)
            advanceUntilIdle()
            val settled = viewModel.uiState.value
            assertNull(settled.errorMessage)
            assertEquals(listOf("操作规程B.docx"), settled.groups.map { it.key })
        }
}
