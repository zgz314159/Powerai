package com.example.powerai.ui.screen.database

import androidx.lifecycle.SavedStateHandle
import com.example.powerai.data.importer.AssetImportDiagnostics
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.domain.usecase.DatabaseUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var useCase: DatabaseUseCase
    private lateinit var importManager: DocumentImportManager
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: DatabaseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        useCase = mock(DatabaseUseCase::class.java)
        importManager = mock(DocumentImportManager::class.java)
        `when`(importManager.importDiagnostics).thenReturn(
            MutableStateFlow(AssetImportDiagnostics())
        )
        `when`(importManager.progress).thenReturn(
            MutableStateFlow<ImportProgress?>(null)
        )
        savedStateHandle = SavedStateHandle()
        viewModel = DatabaseViewModel(useCase, importManager, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be loading with default values`() {
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(state.groups.isEmpty())
        assertEquals("", state.currentQuery)
        assertTrue(state.collapsedGroupKeys.isEmpty())
        assertNull(state.focusTarget)
        assertNull(state.focusGroupKey)
        assertNull(state.selectedItemId)
        assertNull(state.pendingSelectedItemScrollId)
        assertTrue(state.searchHistory.isEmpty())
        assertTrue(state.sourceFileNames.isEmpty())
    }

    @Test
    fun `SelectItem intent should update selectedItemId and pendingSelectedItemScrollId`() {
        viewModel.onIntent(DatabaseIntent.SelectItem(123L))
        val state = viewModel.uiState.value
        assertEquals(123L, state.selectedItemId)
        assertEquals(123L, state.pendingSelectedItemScrollId)
    }

    @Test
    fun `SelectItem with null should clear selection`() {
        viewModel.onIntent(DatabaseIntent.SelectItem(123L))
        viewModel.onIntent(DatabaseIntent.SelectItem(null))
        val state = viewModel.uiState.value
        assertNull(state.selectedItemId)
        assertNull(state.pendingSelectedItemScrollId)
    }

    @Test
    fun `ConsumePendingSelectedItemScroll should clear pendingSelectedItemScrollId`() {
        viewModel.onIntent(DatabaseIntent.SelectItem(123L))
        viewModel.onIntent(DatabaseIntent.ConsumePendingSelectedItemScroll)
        val state = viewModel.uiState.value
        assertEquals(123L, state.selectedItemId)
        assertNull(state.pendingSelectedItemScrollId)
    }

    @Test
    fun `ConsumeFocusTarget should clear focusTarget`() {
        viewModel.onIntent(DatabaseIntent.FocusItem(123L))
        viewModel.onIntent(DatabaseIntent.ConsumeFocusTarget)
        val state = viewModel.uiState.value
        assertNull(state.focusTarget)
    }

    @Test
    fun `ConsumeFocusGroup should clear focusGroupKey`() {
        viewModel.onIntent(DatabaseIntent.FocusGroup("group1"))
        viewModel.onIntent(DatabaseIntent.ConsumeFocusGroup)
        val state = viewModel.uiState.value
        assertNull(state.focusGroupKey)
    }

    @Test
    fun `ToggleCollapsedGroup should add key if not present`() {
        viewModel.onIntent(DatabaseIntent.ToggleCollapsedGroup("group1"))
        val state = viewModel.uiState.value
        assertTrue(state.collapsedGroupKeys.contains("group1"))
    }

    @Test
    fun `ToggleCollapsedGroup should remove key if present`() {
        viewModel.onIntent(DatabaseIntent.ToggleCollapsedGroup("group1"))
        viewModel.onIntent(DatabaseIntent.ToggleCollapsedGroup("group1"))
        val state = viewModel.uiState.value
        assertFalse(state.collapsedGroupKeys.contains("group1"))
    }

    @Test
    fun `SetCollapsedGroupKeys should update collapsedGroupKeys`() {
        val keys = setOf("group1", "group2")
        viewModel.onIntent(DatabaseIntent.SetCollapsedGroupKeys(keys))
        val state = viewModel.uiState.value
        assertEquals(keys, state.collapsedGroupKeys)
    }

    @Test
    fun `initial state should restore currentQuery from SavedStateHandle`() {
        savedStateHandle["database_current_query"] = "saved query"
        val vm = DatabaseViewModel(useCase, importManager, savedStateHandle)
        assertEquals("saved query", vm.uiState.value.currentQuery)
    }

    @Test
    fun `initial state should restore collapsedGroupKeys from SavedStateHandle`() {
        savedStateHandle["database_collapsed_group_keys"] = arrayListOf("g1", "g2")
        val vm = DatabaseViewModel(useCase, importManager, savedStateHandle)
        assertEquals(setOf("g1", "g2"), vm.uiState.value.collapsedGroupKeys)
    }
}
