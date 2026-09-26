package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Characterization tests for [PdfFigureListViewModel].
 *
 * Covers first load, repeat load, blank ids, page ordering, image de
 * duplication, page fallback, empty results, repository failure and lifecycle
 * cancellation. Only fake DAO rows are used; the ViewModel's IO hop is
 * synchronized through StateFlow transitions, never through sleeps.
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

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                body()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun entity(
        source: String = "assets/kb/doc",
        pageNumber: Int? = null,
        blocksJson: String? = null,
    ): KnowledgeEntity =
        KnowledgeEntity(
            title = "title",
            content = "content",
            source = source,
            pageNumber = pageNumber,
            contentBlocksJson = blocksJson,
        )

    private suspend fun awaitLoad(viewModel: PdfFigureListViewModel) {
        viewModel.isLoading.first { it }
        viewModel.isLoading.first { !it }
    }

    @Test
    fun `first load maps images and tables sorted by page`() {
        runVmTest {
            val blocks =
                """
                {"blocks":[
                  {"id":"i9","type":"image","src":"file:///img9.png","page":9},
                  {"id":"t2","type":"table","imageUri":"file:///tab2.png","page":2},
                  {"id":"i4","type":"image","src":"file:///img4.png","caption":"图 1","page":4}
                ]}
                """.trimIndent()
            whenever(dao.sampleBySourcePrefix("assets/kb/doc", 5000))
                .thenReturn(listOf(entity(blocksJson = blocks)))
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("doc")
            awaitLoad(viewModel)

            val items = viewModel.figures.value
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
            assertEquals(false, viewModel.isLoading.value)
        }
    }

    @Test
    fun `repeat load for the same file is a no-op`() {
        runVmTest {
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("doc")
            awaitLoad(viewModel)
            viewModel.loadFor("doc")
            testScheduler.advanceUntilIdle()

            verify(dao, times(1)).sampleBySourcePrefix(any(), any())
            assertTrue(viewModel.figures.value.isEmpty())
        }
    }

    @Test
    fun `blank file ids are ignored`() {
        runVmTest {
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("")
            viewModel.loadFor("   ")
            testScheduler.advanceUntilIdle()

            verify(dao, never()).sampleBySourcePrefix(any(), any())
            assertTrue(viewModel.figures.value.isEmpty())
            assertEquals(false, viewModel.isLoading.value)
        }
    }

    @Test
    fun `different file ids reload with a lowercased prefix`() {
        runVmTest {
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("Alpha")
            awaitLoad(viewModel)
            viewModel.loadFor("beta")
            awaitLoad(viewModel)

            verify(dao).sampleBySourcePrefix("assets/kb/alpha", 5000)
            verify(dao).sampleBySourcePrefix("assets/kb/beta", 5000)
            verify(dao, times(2)).sampleBySourcePrefix(any(), any())
        }
    }

    @Test
    fun `duplicate image uris collapse to a single item`() {
        runVmTest {
            val first =
                """
                {"blocks":[{"id":"a","type":"image","src":"file:///same.png","page":1}]}
                """.trimIndent()
            val second =
                """
                {"blocks":[{"id":"b","type":"image","src":"file:///same.png","page":2}]}
                """.trimIndent()
            whenever(dao.sampleBySourcePrefix(any(), any()))
                .thenReturn(listOf(entity(blocksJson = first), entity(blocksJson = second)))
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("doc")
            awaitLoad(viewModel)

            val items = viewModel.figures.value
            assertEquals(1, items.size)
            assertEquals("file:///same.png", items[0].imageUri)
            assertEquals("a", items[0].id)
        }
    }

    @Test
    fun `missing pages fall back to the entity page and pageless blocks are dropped`() {
        runVmTest {
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
                        entity(pageNumber = 7, blocksJson = blocksOnEntityPage),
                        entity(pageNumber = null, blocksJson = mixed),
                    ),
                )
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("doc")
            awaitLoad(viewModel)

            // withPage keeps its own page 3, withEntity inherits 7, pageless is
            // dropped because neither the block nor the entity carries a page
            val items = viewModel.figures.value
            assertEquals(listOf("withOwnPage", "withEntity"), items.map { it.id })
            assertEquals(listOf(3, 7), items.map { it.pageNumber })
        }
    }

    @Test
    fun `empty rows yield an empty list`() {
        runVmTest {
            whenever(dao.sampleBySourcePrefix(any(), any())).thenReturn(emptyList())
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("doc")
            awaitLoad(viewModel)

            assertTrue(viewModel.figures.value.isEmpty())
            assertEquals(false, viewModel.isLoading.value)
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
                    runTest {
                        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                        val failingDao = mock<KnowledgeDao>()
                        whenever(failingDao.sampleBySourcePrefix(any(), any()))
                            .thenThrow(IllegalStateException("db down"))
                        val viewModel = PdfFigureListViewModel(failingDao)

                        viewModel.loadFor("doc")
                        viewModel.isLoading.first { it }

                        val deadline = System.currentTimeMillis() + 2000
                        while (surfaced == null && System.currentTimeMillis() < deadline) {
                            val drain = runCatching { testScheduler.advanceUntilIdle() }
                            if (drain.isFailure) {
                                surfaced = drain.exceptionOrNull()
                                break
                            }
                        }

                        // state stays loading with no fabricated results
                        assertEquals(true, viewModel.isLoading.value)
                        assertTrue(viewModel.figures.value.isEmpty())
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
        runVmTest {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            whenever(dao.sampleBySourcePrefix(any(), any())).thenAnswer {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                finished.countDown()
                emptyList<KnowledgeEntity>()
            }
            val viewModel = PdfFigureListViewModel(dao)

            viewModel.loadFor("doc")
            testScheduler.advanceUntilIdle()
            assertTrue("dao must have started", entered.await(5, TimeUnit.SECONDS))
            assertEquals(true, viewModel.isLoading.value)

            viewModel.viewModelScope.cancel()
            release.countDown()
            assertTrue("dao must finish", finished.await(5, TimeUnit.SECONDS))
            // drain the cancelled continuation's resume while Main is still the
            // test dispatcher, so nothing leaks into the next test
            val deadline = System.currentTimeMillis() + 2000
            while (System.currentTimeMillis() < deadline) {
                testScheduler.advanceUntilIdle()
            }

            // cancellation prevents the loaded results from being published
            assertEquals(true, viewModel.isLoading.value)
            assertTrue(viewModel.figures.value.isEmpty())
        }
    }
}
