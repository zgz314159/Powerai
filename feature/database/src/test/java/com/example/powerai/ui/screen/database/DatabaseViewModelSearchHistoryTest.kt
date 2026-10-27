package com.example.powerai.ui.screen.database

import com.example.powerai.domain.model.SearchEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Characterization tests for [DatabaseViewModel] search history handling. */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelSearchHistoryTest {
    private val harnesses = mutableListOf<DatabaseViewModelTestHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.cancelCreatedViewModels() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    private fun newHarness(): DatabaseViewModelTestHarness = DatabaseViewModelTestHarness().also { harnesses += it }

    @Test
    fun `initial history load populates state`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.historyRepo.seed(listOf(SearchEntry("旧查询", 100L)))
            val viewModel = harness.createViewModel(this)

            advanceUntilIdle()

            assertEquals(listOf("旧查询"), viewModel.uiState.value.searchHistory.map { it.query })
        }

    @Test
    fun `search history dedupes keeps newest first in state and store`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)

            viewModel.search("alpha")
            advanceUntilIdle()
            viewModel.search("beta")
            advanceUntilIdle()
            viewModel.search("alpha")
            advanceUntilIdle()

            assertEquals(
                listOf("alpha", "beta"),
                viewModel.uiState.value.searchHistory.map { it.query },
            )
            assertEquals(listOf("alpha", "beta"), harness.historyRepo.storedQueries())
        }

    @Test
    fun `clear search history resets state and store`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.historyRepo.seed(listOf(SearchEntry("seed", 1L)))
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()
            assertEquals(listOf("seed"), viewModel.uiState.value.searchHistory.map { it.query })

            viewModel.onIntent(DatabaseIntent.ClearSearchHistory)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchHistory.isEmpty())
            assertTrue(harness.historyRepo.storedQueries().isEmpty())
        }
}
