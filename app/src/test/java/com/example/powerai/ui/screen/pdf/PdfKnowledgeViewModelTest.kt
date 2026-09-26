package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * failure and lifecycle cancellation with fake repository data only. The load
 * path has no IO hop, so the test scheduler drives everything
 * deterministically.
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

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                body()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun item(
        id: Long,
        blocksJson: String? = null,
    ): KnowledgeItem =
        KnowledgeItem(
            id = id,
            title = "title-$id",
            content = "content",
            source = "source.pdf",
            category = "分类",
            keywords = emptyList(),
            contentBlocksJson = blocksJson,
        )

    private val twoBlockJson =
        """
        {"blocks":[
          {"id":"b1","type":"text","text":"你好"},
          {"id":"b2","type":"image","src":"file:///x.png"}
        ]}
        """.trimIndent()

    @Test
    fun `first load reads count items first id and page blocks`() {
        runVmTest {
            whenever(repository.countKnowledgeByPage("file1", 3)).thenReturn(2)
            whenever(repository.getItemsByPage("file1", 3))
                .thenReturn(listOf(item(10L, twoBlockJson), item(20L)))
            val viewModel = PdfKnowledgeViewModel(repository)

            viewModel.updatePage("file1", 3)
            testScheduler.advanceUntilIdle()

            assertEquals(2, viewModel.knowledgeCount.value)
            assertEquals(listOf(10L, 20L), viewModel.pageItems.value.map { it.id })
            assertEquals(10L, viewModel.firstItemId.value)

            val blocks = viewModel.pageBlocks.value
            assertEquals(2, blocks.size)
            assertEquals(10L, blocks[0].first)
            assertTrue(blocks[0].second is TextBlock)
            assertEquals(10L, blocks[1].first)
            assertTrue(blocks[1].second is ImageBlock)
        }
    }

    @Test
    fun `empty page count clears the previous state`() {
        runVmTest {
            whenever(repository.countKnowledgeByPage("file1", 3)).thenReturn(1)
            whenever(repository.getItemsByPage("file1", 3)).thenReturn(listOf(item(10L)))
            whenever(repository.countKnowledgeByPage("file1", 4)).thenReturn(0)
            val viewModel = PdfKnowledgeViewModel(repository)

            viewModel.updatePage("file1", 3)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(10L), viewModel.pageItems.value.map { it.id })

            viewModel.updatePage("file1", 4)
            testScheduler.advanceUntilIdle()

            assertEquals(0, viewModel.knowledgeCount.value)
            assertTrue(viewModel.pageItems.value.isEmpty())
            assertNull(viewModel.firstItemId.value)
            assertTrue(viewModel.pageBlocks.value.isEmpty())
        }
    }

    @Test
    fun `repeat same page is a no-op`() {
        runVmTest {
            whenever(repository.countKnowledgeByPage("file1", 3)).thenReturn(1)
            whenever(repository.getItemsByPage("file1", 3)).thenReturn(listOf(item(10L)))
            val viewModel = PdfKnowledgeViewModel(repository)

            viewModel.updatePage("file1", 3)
            testScheduler.advanceUntilIdle()
            viewModel.updatePage("file1", 3)
            testScheduler.advanceUntilIdle()

            verify(repository, times(1)).countKnowledgeByPage("file1", 3)
            assertEquals(listOf(10L), viewModel.pageItems.value.map { it.id })
        }
    }

    @Test
    fun `blank file ids are ignored`() {
        runVmTest {
            val viewModel = PdfKnowledgeViewModel(repository)

            viewModel.updatePage("", 9)
            viewModel.updatePage("   ", 9)
            testScheduler.advanceUntilIdle()

            verify(repository, never()).countKnowledgeByPage(any(), any())
            assertEquals(0, viewModel.knowledgeCount.value)
            assertTrue(viewModel.pageItems.value.isEmpty())
        }
    }

    @Test
    fun `page switch queries the new page`() {
        runVmTest {
            whenever(repository.countKnowledgeByPage("file1", 1)).thenReturn(1)
            whenever(repository.getItemsByPage("file1", 1)).thenReturn(listOf(item(10L)))
            whenever(repository.countKnowledgeByPage("file1", 2)).thenReturn(0)
            val viewModel = PdfKnowledgeViewModel(repository)

            viewModel.updatePage("file1", 1)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(10L), viewModel.pageItems.value.map { it.id })

            viewModel.updatePage("file1", 2)
            testScheduler.advanceUntilIdle()

            verify(repository).countKnowledgeByPage("file1", 1)
            verify(repository).countKnowledgeByPage("file1", 2)
            assertTrue(viewModel.pageItems.value.isEmpty())
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
                    runTest {
                        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                        whenever(repository.countKnowledgeByPage("file1", 1)).thenReturn(1)
                        whenever(repository.getItemsByPage("file1", 1))
                            .thenReturn(listOf(item(10L)))
                        whenever(repository.countKnowledgeByPage("file1", 2))
                            .thenThrow(IllegalStateException("db down"))
                        val viewModel = PdfKnowledgeViewModel(repository)

                        viewModel.updatePage("file1", 1)
                        testScheduler.advanceUntilIdle()
                        assertEquals(listOf(10L), viewModel.pageItems.value.map { it.id })

                        viewModel.updatePage("file1", 2)
                        val drain = runCatching { testScheduler.advanceUntilIdle() }
                        if (drain.isFailure) {
                            surfaced = drain.exceptionOrNull()
                        }

                        // the previous page survives the failure untouched
                        assertEquals(listOf(10L), viewModel.pageItems.value.map { it.id })
                        assertEquals(10L, viewModel.firstItemId.value)
                        assertEquals(1, viewModel.knowledgeCount.value)
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
        runVmTest {
            val viewModel = PdfKnowledgeViewModel(repository)

            viewModel.viewModelScope.cancel()
            viewModel.updatePage("file1", 1)
            testScheduler.advanceUntilIdle()

            verify(repository, never()).countKnowledgeByPage(any(), any())
            assertEquals(0, viewModel.knowledgeCount.value)
            assertTrue(viewModel.pageItems.value.isEmpty())
            assertNull(viewModel.firstItemId.value)
        }
    }
}
