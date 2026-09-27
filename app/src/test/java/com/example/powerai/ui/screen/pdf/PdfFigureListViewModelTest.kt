package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Characterization tests for [PdfFigureListViewModel].
 *
 * Covers first load, repeat load, blank ids, page ordering, image
 * de-duplication, page fallback, empty results, repository failure and
 * lifecycle cancellation. Only fake DAO rows are used and every wait is
 * driven by the virtual test scheduler — no sleeps, latches or real time.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PdfFigureListViewModelTest {
    private lateinit var dao: KnowledgeDao

    @Before
    fun setUp() {
        dao = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first load maps images and tables sorted by page`() {
        runPdfVmTest {
            val blocks =
                """
                {"blocks":[
                  {"id":"i9","type":"image","src":"file:///img9.png","page":9},
                  {"id":"t2","type":"table","imageUri":"file:///tab2.png","page":2},
                  {"id":"i4","type":"image","src":"file:///img4.png","caption":"图 1","page":4}
                ]}
                """.trimIndent()
            whenever(dao.sampleBySourcePrefix("assets/kb/doc", 5000))
                .thenReturn(listOf(pdfEntity(blocksJson = blocks)))
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()

            val items = viewModel.uiState.value.figures
            assertEquals(listOf(2, 4, 9), items.map { it.pageNumber })
            assertEquals(listOf(true, false, false), items.map { it.isTable })

            val table = items[0]
            assertEquals("t2", table.id)
            assertEquals("表格 · 第 2 页", table.caption)
            assertEquals("file:///tab2.png", table.imageUri)
            assertEquals("assets/kb/doc", table.subfolder)

            val captioned = items[1]
            assertEquals("图 1", captioned.caption)
            assertEquals("file:///img4.png", captioned.imageUri)

            val defaultCaption = items[2]
            assertEquals("图 · 第 9 页", defaultCaption.caption)

            verify(dao).sampleBySourcePrefix("assets/kb/doc", 5000)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun `repeat load for the same file is a no-op`() {
        runPdfVmTest {
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()
            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()

            verify(dao, times(1)).sampleBySourcePrefix(any(), any())
            assertTrue(viewModel.uiState.value.figures.isEmpty())
        }
    }

    @Test
    fun `blank file ids are ignored`() {
        runPdfVmTest {
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load(""))
            viewModel.onIntent(PdfFigureListIntent.Load("   "))
            testScheduler.advanceUntilIdle()

            verify(dao, never()).sampleBySourcePrefix(any(), any())
            assertTrue(viewModel.uiState.value.figures.isEmpty())
            assertEquals(false, viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun `different file ids reload with a lowercased prefix`() {
        runPdfVmTest {
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("Alpha"))
            testScheduler.advanceUntilIdle()
            viewModel.onIntent(PdfFigureListIntent.Load("beta"))
            testScheduler.advanceUntilIdle()

            verify(dao).sampleBySourcePrefix("assets/kb/alpha", 5000)
            verify(dao).sampleBySourcePrefix("assets/kb/beta", 5000)
            verify(dao, times(2)).sampleBySourcePrefix(any(), any())
        }
    }

    @Test
    fun `duplicate image uris collapse to a single item`() {
        runPdfVmTest {
            val first =
                """
                {"blocks":[{"id":"a","type":"image","src":"file:///same.png","page":1}]}
                """.trimIndent()
            val second =
                """
                {"blocks":[{"id":"b","type":"image","src":"file:///same.png","page":2}]}
                """.trimIndent()
            whenever(dao.sampleBySourcePrefix(any(), any()))
                .thenReturn(listOf(pdfEntity(blocksJson = first), pdfEntity(blocksJson = second)))
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()

            val items = viewModel.uiState.value.figures
            assertEquals(1, items.size)
            assertEquals("file:///same.png", items[0].imageUri)
            assertEquals("a", items[0].id)
        }
    }

    @Test
    fun `missing pages fall back to the entity page and pageless blocks are dropped`() {
        runPdfVmTest {
            val blocksOnEntityPage =
                """
                {"blocks":[{"id":"withEntity","type":"image","src":"file:///entity-page.png"}]}
                """.trimIndent()
            val mixed =
                """
                {"blocks":[
                  {"id":"withOwnPage","type":"image","src":"file:///own.png","page":3},
                  {"id":"pageless","type":"image","src":"file:///gone.png"}
                ]}
                """.trimIndent()
            whenever(dao.sampleBySourcePrefix(any(), any()))
                .thenReturn(
                    listOf(
                        pdfEntity(pageNumber = 7, blocksJson = blocksOnEntityPage),
                        pdfEntity(pageNumber = null, blocksJson = mixed),
                    ),
                )
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()

            // withPage keeps its own page 3, withEntity inherits 7, pageless is
            // dropped because neither the block nor the entity carries a page
            val items = viewModel.uiState.value.figures
            assertEquals(listOf("withOwnPage", "withEntity"), items.map { it.id })
            assertEquals(listOf(3, 7), items.map { it.pageNumber })
        }
    }

    @Test
    fun `empty rows yield an empty list`() {
        runPdfVmTest {
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.figures.isEmpty())
            assertEquals(false, viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun `dao failure surfaces without corrupting the current state`() {
        var surfaced: Throwable? = null
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> surfaced = error }
        try {
            val outcome =
                runCatching {
                    runPdfVmTest {
                        val failingDao = mock<KnowledgeDao>()
                        whenever(failingDao.sampleBySourcePrefix(any(), any()))
                            .thenThrow(IllegalStateException("db down"))
                        val viewModel = pdfFigureViewModel(failingDao)

                        viewModel.onIntent(PdfFigureListIntent.Load("doc"))
                        try {
                            testScheduler.advanceUntilIdle()
                        } catch (error: Throwable) {
                            surfaced = surfaced ?: error
                        }

                        // state stays loading with no fabricated results
                        assertEquals(true, viewModel.uiState.value.isLoading)
                        assertTrue(viewModel.uiState.value.figures.isEmpty())
                    }
                }
            if (surfaced == null) {
                surfaced = outcome.exceptionOrNull()
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            Dispatchers.resetMain()
        }

        assertNotNull("the dao failure must surface somewhere", surfaced)
        assertTrue(
            "expected the dao error, got $surfaced",
            surfaced?.message.orEmpty().contains("db down"),
        )
    }

    @Test
    fun `cancelling the scope mid load freezes the state`() {
        runPdfVmTest {
            val gate = CompletableDeferred<Unit>()
            whenever(dao.sampleBySourcePrefix(any(), any())).doSuspendableAnswer {
                gate.await()
                emptyList<KnowledgeEntity>()
            }
            val viewModel = pdfFigureViewModel(dao)

            viewModel.onIntent(PdfFigureListIntent.Load("doc"))
            testScheduler.advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value.isLoading)

            viewModel.viewModelScope.cancel()
            gate.complete(Unit)
            testScheduler.advanceUntilIdle()

            // cancellation prevents the loaded results from being published
            assertEquals(true, viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.figures.isEmpty())
        }
    }
}
