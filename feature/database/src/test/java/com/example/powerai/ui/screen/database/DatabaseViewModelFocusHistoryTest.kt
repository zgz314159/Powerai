package com.example.powerai.ui.screen.database

import androidx.lifecycle.SavedStateHandle
import com.example.powerai.domain.model.DatabaseFocusTarget
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Characterization tests for [DatabaseViewModel] focus and search history. */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelFocusHistoryTest {
    private val harnesses = mutableListOf<DatabaseViewModelTestHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.cancelCreatedViewModels() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    private fun newHarness(): DatabaseViewModelTestHarness = DatabaseViewModelTestHarness().also { harnesses += it }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedTwoGroupViewModel(
        harness: DatabaseViewModelTestHarness,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): DatabaseViewModel {
        harness.repo.allItems =
            listOf(
                knowledgeItem(1, "接地要求"),
                knowledgeItem(3, "验电要求", source = "操作规程B.docx"),
            )
        val viewModel = harness.createViewModel(this, savedStateHandle)
        viewModel.loadAll()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `focus item sets focus state clears query and reloads`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val savedStateHandle = SavedStateHandle()
            val viewModel = harness.createViewModel(this, savedStateHandle)
            viewModel.loadAll()
            advanceUntilIdle()
            val loadsBeforeFocus = harness.repo.loadCount

            viewModel.focusItem(1L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1L, state.selectedItemId)
            assertEquals(1L, state.pendingSelectedItemScrollId)
            assertEquals(DatabaseFocusTarget(itemId = 1L), state.focusTarget)
            assertEquals("", state.currentQuery)
            assertEquals("", savedStateHandle.get<String>("database_current_query"))
            assertEquals(loadsBeforeFocus + 1, harness.repo.loadCount)
            assertTrue(state.groups.any { group -> group.rows.any { it.item.id == 1L } })
        }

    @Test
    fun `focus item with details builds full target and prefetches file name`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val detail =
                knowledgeItem(
                    id = 5L,
                    title = "接地要求",
                    source = pdfSource("班组手册.pdf"),
                    content = "导体接地电阻不大于4欧",
                    pageNumber = 7,
                    contextLabel = "手册封面",
                )
            harness.repo.itemsById = mapOf(5L to detail)
            val viewModel = harness.createViewModel(this)

            viewModel.focusItem(detail)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                DatabaseFocusTarget(
                    itemId = 5L,
                    title = "接地要求",
                    source = "手册封面",
                    pageNumber = 7,
                    content = "导体接地电阻不大于4欧",
                ),
                state.focusTarget,
            )
            assertEquals("班组手册.pdf", state.sourceFileNames[5L])
            assertEquals("", state.currentQuery)
        }

    @Test
    fun `non-positive focus item ids are ignored`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = harness.createViewModel(this)

            viewModel.onIntent(DatabaseIntent.FocusItem(0L))
            viewModel.focusItem(-3L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.focusTarget)
            assertNull(state.selectedItemId)
            assertNull(state.pendingSelectedItemScrollId)
            assertEquals(0, harness.repo.loadCount)
        }

    @Test
    fun `focus group expands target collapses others and clears selection`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val savedStateHandle = SavedStateHandle()
            val viewModel = loadedTwoGroupViewModel(harness, savedStateHandle)
            viewModel.onIntent(
                DatabaseIntent.SetCollapsedGroupKeys(setOf("班组手册A.docx", "操作规程B.docx")),
            )

            viewModel.focusGroup("操作规程B.docx")

            val state = viewModel.uiState.value
            assertEquals(setOf("班组手册A.docx"), state.collapsedGroupKeys)
            assertEquals("操作规程B.docx", state.focusGroupKey)
            assertNull(state.selectedItemId)
            assertNull(state.pendingSelectedItemScrollId)
            assertNull(state.focusTarget)
            assertEquals(
                arrayListOf("班组手册A.docx"),
                savedStateHandle.get<ArrayList<String>>("database_collapsed_group_keys"),
            )
        }

    @Test
    fun `focus group missing from current groups still records key`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = loadedTwoGroupViewModel(harness)

            viewModel.focusGroup("ghost-group")

            val state = viewModel.uiState.value
            assertEquals("ghost-group", state.focusGroupKey)
            assertTrue(state.collapsedGroupKeys.isEmpty())
            assertNull(state.selectedItemId)
        }

    @Test
    fun `focus group ignores blank key`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            val viewModel = loadedTwoGroupViewModel(harness)

            viewModel.focusGroup("   ")

            assertNull(viewModel.uiState.value.focusGroupKey)
        }

    @Test
    fun `drawer focus restores directory groups without reload`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.searchItems = { query ->
                if (query == "alpha") listOf(knowledgeItem(1, "接地要求")) else emptyList()
            }
            val savedStateHandle = SavedStateHandle()
            val viewModel = loadedTwoGroupViewModel(harness, savedStateHandle)
            viewModel.search("alpha")
            advanceUntilIdle()
            val loadsBeforeDrawerFocus = harness.repo.loadCount
            assertTrue(viewModel.uiState.value.groups.size < 2)

            viewModel.focusGroupFromDrawer("操作规程B.docx")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("班组手册A.docx", "操作规程B.docx"), state.groups.map { it.key })
            assertEquals("操作规程B.docx", state.focusGroupKey)
            assertEquals(setOf("班组手册A.docx"), state.collapsedGroupKeys)
            assertEquals("", state.currentQuery)
            assertEquals(loadsBeforeDrawerFocus, harness.repo.loadCount)
            assertEquals(
                arrayListOf("班组手册A.docx"),
                savedStateHandle.get<ArrayList<String>>("database_collapsed_group_keys"),
            )
        }

    @Test
    fun `drawer focus with empty directory groups falls back to load`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)

            viewModel.focusGroupFromDrawer("班组手册A.docx")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("班组手册A.docx", state.focusGroupKey)
            assertEquals(1, harness.repo.loadCount)
            assertEquals(listOf("班组手册A.docx"), state.groups.map { it.key })
        }

    @Test
    fun `missing focus target survives reload until consumed`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)
            viewModel.loadAll()
            advanceUntilIdle()

            viewModel.focusItem(99L)
            advanceUntilIdle()
            assertEquals(DatabaseFocusTarget(itemId = 99L), viewModel.uiState.value.focusTarget)

            viewModel.onIntent(DatabaseIntent.ConsumeFocusTarget)
            assertNull(viewModel.uiState.value.focusTarget)
        }

    @Test
    fun `blank search query loads all without history entry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = newHarness()
            harness.historyRepo.seed(listOf(SearchEntry("seed", 1L)))
            harness.repo.allItems = listOf(knowledgeItem(1, "接地要求"))
            val viewModel = harness.createViewModel(this)
            advanceUntilIdle()

            viewModel.search("   ")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.currentQuery)
            assertEquals(listOf("seed"), state.searchHistory.map { it.query })
            assertEquals(listOf("seed"), harness.historyRepo.storedQueries())
            assertEquals(1, harness.repo.loadCount)
        }
}
