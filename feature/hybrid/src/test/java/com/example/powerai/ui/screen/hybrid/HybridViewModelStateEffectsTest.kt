package com.example.powerai.ui.screen.hybrid

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.domain.model.QueryResult
import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Characterization tests for [HybridViewModel] state reducers, intent
 * routing of UI-control/history/import intents, scope cancellation and the
 * (currently empty) effect channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelStateEffectsTest {
    @Test
    fun `ui control intents route into state reducers`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)

            viewModel.onIntent(HybridIntent.SetWebSearchEnabled(true))
            viewModel.onIntent(HybridIntent.SetLocalCurrentPage(3))
            viewModel.onIntent(HybridIntent.ToggleLocalCollapsedGroup("g1"))
            viewModel.onIntent(HybridIntent.SetLocalCollapsedGroupKeys(setOf("a", "b")))
            viewModel.onIntent(HybridIntent.SetLocalScrollPosition(4, 12))
            viewModel.onIntent(HybridIntent.SetSmartScrollPosition(5, 20))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.webSearchEnabled)
            assertEquals(3, state.localCurrentPage)
            assertEquals(setOf("a", "b"), state.localCollapsedGroupKeys)
            assertEquals(4, state.localScrollIndex)
            assertEquals(12, state.localScrollOffset)
            assertEquals(5, state.smartScrollIndex)
            assertEquals(20, state.smartScrollOffset)
        }

    @Test
    fun `clear results intent resets answer and local summary`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.invoke(any()))
                .thenReturn(QueryResult(answer = "before clear", references = emptyList(), confidence = 1f))
            viewModel.submitQuery("to be cleared", DisplayMode.SMART)
            advanceUntilIdle()
            assertEquals("before clear", viewModel.uiState.value.answer)

            viewModel.onIntent(HybridIntent.ClearResults)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.answer)
            assertEquals("", state.question)
            assertEquals("", state.sanitizedQuestion)
            assertFalse(state.isLoading)
            assertNull(state.askedAtMillis)
            assertEquals("", state.localSummaryState.query)
            assertTrue(state.evidenceList.isEmpty())
        }

    @Test
    fun `import progress flows into state`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            advanceUntilIdle()

            val progress =
                ImportProgress(
                    fileId = "f1",
                    fileName = "doc.pdf",
                    totalItems = 5,
                    importedItems = 2,
                    percent = 40,
                    status = "in_progress",
                )
            (harness.importer.progress as kotlinx.coroutines.flow.MutableStateFlow<ImportProgress?>).value = progress
            advanceUntilIdle()

            assertEquals(progress, viewModel.uiState.value.importProgress)
        }

    @Test
    fun `effect channel stays empty across submissions`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.aiMode(any(), any())).thenReturn("answer")
            val effects = mutableListOf<HybridEffect>()
            val collector = launch { viewModel.effect.collect { effects += it } }

            viewModel.submitQuery("effect question", DisplayMode.AI)
            advanceUntilIdle()
            viewModel.onIntent(HybridIntent.ClearResults)
            advanceUntilIdle()

            assertTrue(effects.isEmpty())
            collector.cancel()
        }

    @Test
    fun `scope cancellation keeps submission preparation but skips execution`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            viewModel.viewModelScope.cancel()

            viewModel.submitQuery("cancelled question", DisplayMode.AI)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            // preparation applies synchronously before the (cancelled) launch
            assertEquals("cancelled question", state.question)
            assertTrue(state.isLoading)
            Mockito.verify(harness.queryUseCase, Mockito.never()).aiMode(any(), any())
        }

    @Test
    fun `smart history intents trim record and clear`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            advanceUntilIdle()

            viewModel.onIntent(HybridIntent.RecordSmartSearchQuery("  padded query  "))
            viewModel.onIntent(HybridIntent.RecordSmartSearchQuery("   "))
            advanceUntilIdle()
            assertEquals(listOf("padded query"), harness.historyUseCase.smartHistory.value.map { it.query })
            assertEquals(listOf("padded query"), viewModel.uiState.value.smartSearchHistory.map { it.query })

            viewModel.onIntent(HybridIntent.ClearSmartSearchHistory)
            advanceUntilIdle()
            assertTrue(harness.historyUseCase.smartHistory.value.isEmpty())
            assertTrue(viewModel.uiState.value.smartSearchHistory.isEmpty())

            val sanitized = HybridUtils.sanitizeQuestion("local record")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenReturn(localModeResult(sanitized))
            viewModel.submitQuery("local record", DisplayMode.LOCAL)
            advanceUntilIdle()
            viewModel.onIntent(HybridIntent.ClearLocalSearchHistory)
            advanceUntilIdle()
            assertTrue(harness.historyUseCase.localHistory.value.isEmpty())
            assertTrue(viewModel.uiState.value.localSearchHistory.isEmpty())
        }

    @Test
    fun `import document intent imports and replays the current question`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.aiMode(any(), any())).thenReturn("cached answer")
            viewModel.submitQuery("previous question", DisplayMode.AI)
            advanceUntilIdle()
            val replayFts = HybridUtils.normalizeForFts(HybridUtils.sanitizeQuestion("previous question"))
            whenever(harness.queryUseCase.invoke(replayFts))
                .thenReturn(QueryResult(answer = "replay answer", references = emptyList(), confidence = 1f))
            val uri = Mockito.mock(android.net.Uri::class.java)

            viewModel.onIntent(HybridIntent.ImportDocument(uri))
            advanceUntilIdle()

            verify(harness.importer).importUri(uri)
            // the follow-up replays the current question through the default SMART path
            verify(harness.queryUseCase).invoke(replayFts)
            assertEquals("replay answer", viewModel.uiState.value.answer)
        }

    @Test
    fun `import document without a current question skips the replay`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            advanceUntilIdle()
            val uri = Mockito.mock(android.net.Uri::class.java)

            viewModel.onIntent(HybridIntent.ImportDocument(uri))
            advanceUntilIdle()

            verify(harness.importer).importUri(uri)
            verify(harness.queryUseCase, Mockito.never()).aiMode(any(), any())
            verify(harness.queryUseCase, Mockito.never()).invoke(any())
        }

    @Test
    fun `web search flag is read at execution time`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.aiMode(any(), any())).thenReturn("web answer")
            viewModel.onIntent(HybridIntent.SetWebSearchEnabled(true))

            viewModel.submitQuery("web question", DisplayMode.AI)
            advanceUntilIdle()

            verify(harness.queryUseCase).aiMode("web question", webSearchEnabled = true)
            assertEquals("web answer", viewModel.uiState.value.answer)
        }
}
