package com.example.powerai.ui.screen.pdf

import com.example.powerai.core.data.dao.KnowledgeDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * MVI contract coverage for [PdfFigureListViewModel]: effect channel and
 * deterministic newest-request-wins behaviour under concurrent loads.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PdfFigureListMviContractTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `effect channel stays empty across loads`() {
        runPdfVmTest {
            val dao = mock<KnowledgeDao>()
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = pdfFigureViewModel(dao)
            val effects = mutableListOf<PdfFigureListEffect>()
            val collector = launch { viewModel.effect.collect { effects += it } }

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()

            assertTrue(effects.isEmpty())
            collector.cancel()
        }
    }

    @Test
    fun `concurrent loads resolve to the newest request deterministically`() {
        runPdfVmTest {
            val dao = mock<KnowledgeDao>()
            val gate = CompletableDeferred<Unit>()
            var firstLoadCancelled = false
            val oldBlocks =
                """
                {"blocks":[{"id":"old","type":"image","src":"file:///old.png","page":1}]}
                """.trimIndent()
            val newBlocks =
                """
                {"blocks":[{"id":"new","type":"image","src":"file:///new.png","page":2}]}
                """.trimIndent()
            whenever(dao.sampleBySourcePrefix("assets/kb/doc", 5000)).doSuspendableAnswer {
                try {
                    gate.await()
                } catch (error: CancellationException) {
                    firstLoadCancelled = true
                    throw error
                }
                listOf(pdfEntity(blocksJson = oldBlocks))
            }
            whenever(dao.sampleBySourcePrefix("assets/kb/beta", 5000))
                .thenReturn(listOf(pdfEntity(blocksJson = newBlocks)))
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value.isLoading)

            viewModel.onIntent(PdfFigureListIntent.Load("beta"))
            testScheduler.advanceUntilIdle()

            assertTrue(firstLoadCancelled)
            val state = viewModel.uiState.value
            assertEquals("beta", state.loadedFileId)
            assertFalse(state.isLoading)
            assertEquals(listOf("new"), state.figures.map { it.id })

            gate.complete(Unit)
            testScheduler.advanceUntilIdle()
            val settled = viewModel.uiState.value
            assertEquals(listOf("new"), settled.figures.map { it.id })
            assertFalse(settled.isLoading)
        }
    }
}
