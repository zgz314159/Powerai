package com.example.powerai.ui.screen.hybrid

import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

/**
 * The "本地" page must distinguish a genuinely empty result set from a failed
 * retrieval / import step. Before this fix a thrown [Throwable] was mapped to
 * an empty result set, so the UI rendered the ordinary "no match" empty state.
 * These tests pin the corrected final [HybridUiState] (UI state + visible copy).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSearchFailureUiStateTest {
    @Test
    fun `local success with hits is not a failure`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("有结果")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenReturn(localModeResult(sanitized, listOf(knowledgeItem(1L))))

            viewModel.submitQuery("有结果", DisplayMode.LOCAL)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1L), state.references.map { it.id })
            assertFalse(state.isLoading)
            assertNotEquals(LocalPageState.ERROR, state.localSummaryState.pageState)
        }

    @Test
    fun `local success with zero hits keeps the no-match experience`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("没有匹配")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenReturn(localModeResult(sanitized, emptyList()))

            viewModel.submitQuery("没有匹配", DisplayMode.LOCAL)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.references.isEmpty())
            assertTrue(state.evidenceList.isEmpty())
            assertFalse(state.isLoading)
            assertEquals(LocalPageState.INSUFFICIENT_EVIDENCE, state.localSummaryState.pageState)
        }

    @Test
    fun `local retrieval failure is not disguised as a zero-hit result`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenThrow(IllegalStateException("retrieval exploded"))

            viewModel.submitQuery("检索异常", DisplayMode.LOCAL)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.references.isEmpty())
            assertTrue(state.evidenceList.isEmpty())
            assertFalse(state.isLoading)
            assertEquals(LocalPageState.ERROR, state.localSummaryState.pageState)
            assertFalse(state.localSummaryState.errorMessage.orEmpty().contains("retrieval exploded"))
        }

    @Test
    fun `local import failure is not disguised as a zero-hit result`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.importer.importAssetsIfNeed(any()))
                .thenThrow(IllegalStateException("assets exploded"))
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenReturn(localModeResult(HybridUtils.sanitizeQuestion("导入异常")))

            viewModel.submitQuery("导入异常", DisplayMode.LOCAL)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.references.isEmpty())
            assertFalse(state.isLoading)
            assertEquals(LocalPageState.ERROR, state.localSummaryState.pageState)
            assertFalse(state.localSummaryState.errorMessage.orEmpty().contains("assets exploded"))
        }

    @Test
    fun `cancelled stale failure cannot overwrite the fresh success`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            val secondSanitized = HybridUtils.sanitizeQuestion("fresh query")
            var calls = 0
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer {
                calls += 1
                if (calls == 1) {
                    gate.await()
                    throw IllegalStateException("stale exploded")
                }
                localModeResult(secondSanitized, listOf(knowledgeItem(2L)))
            }

            viewModel.submitQuery("stale query", DisplayMode.LOCAL)
            advanceUntilIdle()
            viewModel.submitQuery("fresh query", DisplayMode.LOCAL)
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(secondSanitized, state.localSummaryState.query)
            assertEquals(listOf(2L), state.references.map { it.id })
            assertNotEquals(LocalPageState.ERROR, state.localSummaryState.pageState)
            assertEquals(listOf(secondSanitized), harness.historyUseCase.localHistory.value.map { it.query })
        }
}
