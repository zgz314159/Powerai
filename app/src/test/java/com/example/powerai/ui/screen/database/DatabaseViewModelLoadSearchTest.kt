package com.example.powerai.ui.screen.database

import androidx.lifecycle.SavedStateHandle
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

/**
 * Characterization tests for [DatabaseViewModel] load / search / import /
 * retry orchestration. All collaborators are deterministic fakes driven by a
 * single test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelLoadSearchTest {
    private val harnesses = mutableListOf<DatabaseViewModelTestHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.cancelCreatedViewModels() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    private fun newHarness(): DatabaseViewModelTestHarness = DatabaseViewModelTestHarness().also { harnesses += it }

    @Test
    fun `load all populates groups directoryGroups and clears loading`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems =
                listOf(
                    knowledgeItem(1, "接地要求"),
                    knowledgeItem(2, "验电要求"),
                )
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.groups.size)
            assertEquals("班组手册A.docx", state.groups[0].key)
            assertEquals(2, state.groups[0].rows.size)
            assertEquals(state.groups, viewModel.directoryGroups.value)
        }

    @Test
    fun `empty load triggers asset import retry until content arrives`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            Mockito.doAnswer {
                harness.repo.allItems = listOf(knowledgeItem(1, "导入后条目"))
                Unit
            }.whenever(harness.importer).importAssetsIfNeed()
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.groups.size)
            Mockito.verify(harness.importer, Mockito.times(1)).importAssetsIfNeed()
        }

    @Test
    fun `empty load even after import shows empty state without error`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertTrue(state.groups.isEmpty())
            Mockito.verify(harness.importer, Mockito.times(1)).importAssetsIfNeed()
        }

    @Test
    fun `load failure surfaces message and resets loading`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.loadError = IllegalStateException("db down")
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("db down", state.errorMessage)
            assertTrue(state.groups.isEmpty())
        }

    @Test
    fun `load failure without message falls back to default text`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.loadError = IllegalArgumentException()
            val viewModel = harness.createViewModel(this)

            viewModel.loadAll()
            advanceUntilIdle()

            assertEquals("加载失败", viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `search trims query returns matches and records history`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            harness.repo.searchItems = { query ->
                if (query == "接地电阻") {
                    listOf(knowledgeItem(3, "接地电阻限值", source = "操作规程B.docx"))
                } else {
                    emptyList()
                }
            }
            val savedStateHandle = SavedStateHandle()
            val viewModel = harness.createViewModel(this, savedStateHandle)

            viewModel.search("  接地电阻  ")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals("接地电阻", state.currentQuery)
            assertEquals(1, state.groups.size)
            assertEquals("操作规程B.docx", state.groups[0].key)
            assertEquals(listOf("接地电阻"), state.searchHistory.map { it.query })
            assertEquals("接地电阻", savedStateHandle.get<String>("database_current_query"))
            assertEquals(listOf("班组手册A.docx"), viewModel.directoryGroups.value.map { it.key })
            assertEquals(listOf("接地电阻"), harness.historyRepo.storedQueries())
        }

    @Test
    fun `empty search result keeps no error and backfills directoryGroups`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            harness.repo.searchItems = { emptyList() }
            val viewModel = harness.createViewModel(this)

            viewModel.search("不存在的关键词")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertTrue(state.groups.isEmpty())
            assertEquals(listOf("班组手册A.docx"), viewModel.directoryGroups.value.map { it.key })
            assertEquals(listOf("不存在的关键词"), state.searchHistory.map { it.query })
        }

    @Test
    fun `search failure surfaces message but still records history`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.searchError = IllegalStateException("index down")
            val viewModel = harness.createViewModel(this)

            viewModel.search("接地")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("index down", state.errorMessage)
            assertTrue(state.groups.isEmpty())
            assertTrue(viewModel.directoryGroups.value.isEmpty())
            assertEquals(listOf("接地"), state.searchHistory.map { it.query })
        }

    @Test
    fun `consecutive searches keep newest result and history order`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            harness.repo.searchItems = { query ->
                if (query == "beta") {
                    listOf(knowledgeItem(3, "beta条目", source = "操作规程B.docx"))
                } else {
                    listOf(knowledgeItem(1, "alpha条目"))
                }
            }
            val viewModel = harness.createViewModel(this)

            viewModel.search("alpha")
            advanceUntilIdle()
            viewModel.search("beta")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("beta", state.currentQuery)
            assertEquals(listOf("操作规程B.docx"), state.groups.map { it.key })
            assertEquals(listOf("beta", "alpha"), state.searchHistory.map { it.query })
            assertEquals(listOf("beta", "alpha"), harness.historyRepo.storedQueries())
        }

    @Test
    fun `refresh intent retries after failure and clears error`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.loadError = IllegalStateException("db down")
            val viewModel = harness.createViewModel(this)

            viewModel.onIntent(DatabaseIntent.Refresh)
            advanceUntilIdle()
            assertEquals("db down", viewModel.uiState.value.errorMessage)

            harness.repo.loadError = null
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            viewModel.onIntent(DatabaseIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.errorMessage)
            assertFalse(state.isLoading)
            assertEquals(1, state.groups.size)
        }
}
