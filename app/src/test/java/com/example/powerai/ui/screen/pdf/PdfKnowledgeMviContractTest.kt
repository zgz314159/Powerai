package com.example.powerai.ui.screen.pdf

import com.example.powerai.core.repository.KnowledgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * MVI contract coverage for [PdfKnowledgeViewModel]: effect channel and
 * deterministic newest-request-wins behaviour under concurrent page updates.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PdfKnowledgeMviContractTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `effect channel stays empty across page updates`() {
        runPdfVmTest {
            val repository = mock<KnowledgeRepository>()
            whenever(repository.countKnowledgeByPage("file1", 1)).thenReturn(0)
            val viewModel = pdfKnowledgeViewModel(repository)
            val effects = mutableListOf<PdfKnowledgeEffect>()
            val collector = launch { viewModel.effect.collect { effects += it } }

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 1))
            testScheduler.advanceUntilIdle()

            assertTrue(effects.isEmpty())
            collector.cancel()
        }
    }

    @Test
    fun `concurrent page updates resolve to the newest page deterministically`() {
        runPdfVmTest {
            val repository = mock<KnowledgeRepository>()
            val gate = CompletableDeferred<Unit>()
            var firstPageCancelled = false
            whenever(repository.countKnowledgeByPage("file1", 1)).doSuspendableAnswer {
                try {
                    gate.await()
                } catch (error: CancellationException) {
                    firstPageCancelled = true
                    throw error
                }
                1
            }
            whenever(repository.getItemsByPage("file1", 1)).thenReturn(listOf(pdfItem(10L)))
            whenever(repository.countKnowledgeByPage("file1", 2)).thenReturn(0)
            val viewModel = pdfKnowledgeViewModel(repository)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 1))
            testScheduler.advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)

            viewModel.onIntent(PdfKnowledgeIntent.UpdatePage("file1", 2))
            testScheduler.advanceUntilIdle()

            assertTrue(firstPageCancelled)
            val state = viewModel.uiState.value
            assertEquals(2, state.page)
            assertEquals(0, state.knowledgeCount)
            assertTrue(state.pageItems.isEmpty())
            assertNull(state.firstItemId)
            assertTrue(state.pageBlocks.isEmpty())

            gate.complete(Unit)
            testScheduler.advanceUntilIdle()
            val settled = viewModel.uiState.value
            assertEquals(2, settled.page)
            assertEquals(0, settled.knowledgeCount)
            assertTrue(settled.pageItems.isEmpty())
        }
    }
}
