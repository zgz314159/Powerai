package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Characterization tests for [PdfKnowledgeViewModel].
 *
 * Covers first load, repeat load, empty results, page switching, repository
 * failure and lifecycle cancellation with fake repository data only; every
 * wait is driven by the virtual test scheduler.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PdfKnowledgeViewModelTest {
    private lateinit var repository: KnowledgeRepository

    @Before
    fun setUp() {
        repository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val twoBlockJson =
        """
        {"blocks":[
          {"id":"b1","type":"text","text":"你好"},
          {"id":"b2","type":"image","src":"file:///x.png"}
        ]}
        """.trimIndent()

    @Test
    fun `first load reads count items first id and page blocks`() {
        runPdfVmTest {
            whenever(repository.countKnowledgeByPage("file1", 3)).thenReturn(2)
            whenever(repository.getItemsByPage("file1", 3))
                .thenReturn(listOf(pdfItem(10L, twoBlockJson), pdfItem(20L)))
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 3))
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.knowledgeCount)
            assertEquals(listOf(10L, 20L), state.pageItems.map { it.id })
            assertEquals(10L, state.firstItemId)

            val blocks = state.pageBlocks
            assertEquals(2, blocks.size)
            assertEquals(10L, blocks[0].first)
            assertTrue(blocks[0].second is TextBlock)
            assertEquals(10L, blocks[1].first)
            assertTrue(blocks[1].second is ImageBlock)
        }
    }

    @Test
    fun `empty page count clears the previous state`() {
        runPdfVmTest {
            whenever(repository.countKnowledgeByPage("file1", 3)).thenReturn(1)
            whenever(repository.getItemsByPage("file1", 3)).thenReturn(listOf(pdfItem(10L)))
            whenever(repository.countKnowledgeByPage("file1", 4)).thenReturn(0)
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 3))
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(10L), viewModel.uiState.value.pageItems.map { it.id })

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 4))
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, state.knowledgeCount)
            assertTrue(state.pageItems.isEmpty())
            assertNull(state.firstItemId)
            assertTrue(state.pageBlocks.isEmpty())
        }
    }

    @Test
    fun `repeat same page is a no-op`() {
        runPdfVmTest {
            whenever(repository.countKnowledgeByPage("file1", 3)).thenReturn(1)
            whenever(repository.getItemsByPage("file1", 3)).thenReturn(listOf(pdfItem(10L)))
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 3))
            testScheduler.advanceUntilIdle()
            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 3))
            testScheduler.advanceUntilIdle()

            verify(repository, times(1)).countKnowledgeByPage("file1", 3)
            assertEquals(listOf(10L), viewModel.uiState.value.pageItems.map { it.id })
        }
    }

    @Test
    fun `blank file ids are ignored`() {
        runPdfVmTest {
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("", 9))
            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("   ", 9))
            testScheduler.advanceUntilIdle()

            verify(repository, never()).countKnowledgeByPage(any(), any())
            assertEquals(0, viewModel.uiState.value.knowledgeCount)
            assertTrue(viewModel.uiState.value.pageItems.isEmpty())
        }
    }

    @Test
    fun `page switch queries the new page`() {
        runPdfVmTest {
            whenever(repository.countKnowledgeByPage("file1", 1)).thenReturn(1)
            whenever(repository.getItemsByPage("file1", 1)).thenReturn(listOf(pdfItem(10L)))
            whenever(repository.countKnowledgeByPage("file1", 2)).thenReturn(0)
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 1))
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(10L), viewModel.uiState.value.pageItems.map { it.id })

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 2))
            testScheduler.advanceUntilIdle()

            verify(repository).countKnowledgeByPage("file1", 1)
            verify(repository).countKnowledgeByPage("file1", 2)
            assertTrue(viewModel.uiState.value.pageItems.isEmpty())
        }
    }

    @Test
    fun `repository failure surfaces without corrupting the previous state`() {
        var surfaced: Throwable? = null
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> surfaced = error }
        try {
            val outcome =
                runCatching {
                    runPdfVmTest {
                        whenever(repository.countKnowledgeByPage("file1", 1)).thenReturn(1)
                        whenever(repository.getItemsByPage("file1", 1))
                            .thenReturn(listOf(pdfItem(10L)))
                        whenever(repository.countKnowledgeByPage("file1", 2))
                            .thenThrow(IllegalStateException("db down"))
                        val viewModel = pdfKnowledgeViewModel(repository)

                        viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 1))
                        testScheduler.advanceUntilIdle()
                        assertEquals(listOf(10L), viewModel.uiState.value.pageItems.map { it.id })

                        viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 2))
                        try {
                            testScheduler.advanceUntilIdle()
                        } catch (error: Throwable) {
                            surfaced = surfaced ?: error
                        }

                        // the previous page survives the failure untouched
                        val state = viewModel.uiState.value
                        assertEquals(listOf(10L), state.pageItems.map { it.id })
                        assertEquals(10L, state.firstItemId)
                        assertEquals(1, state.knowledgeCount)
                    }
                }
            if (surfaced == null) {
                surfaced = outcome.exceptionOrNull()
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            Dispatchers.resetMain()
        }

        assertNotNull("the repository failure must surface somewhere", surfaced)
        assertTrue(
            "expected the dao error, got $surfaced",
            surfaced?.message.orEmpty().contains("db down"),
        )
    }

    @Test
    fun `cancelling the view model scope makes update page inert`() {
        runPdfVmTest {
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.viewModelScope.cancel()
            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 1))
            testScheduler.advanceUntilIdle()

            verify(repository, never()).countKnowledgeByPage(any(), any())
            assertEquals(0, viewModel.uiState.value.knowledgeCount)
            assertTrue(viewModel.uiState.value.pageItems.isEmpty())
            assertNull(viewModel.uiState.value.firstItemId)
        }
    }
}
