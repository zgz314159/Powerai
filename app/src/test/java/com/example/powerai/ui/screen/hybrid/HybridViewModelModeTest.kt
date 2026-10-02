package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.model.QueryResult
import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

/**
 * Characterization tests for [HybridViewModel] mode orchestration: intent
 * routing into Local/AI/Smart, success, empty, failure and streaming
 * ordering — all on the virtual scheduler with fake collaborators.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelModeTest {
    @Test
    fun `submit query intent routes to smart mode by default`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("hello world")
            val fts = HybridUtils.normalizeForFts(sanitized)
            whenever(harness.queryUseCase.invoke(fts)).thenReturn(
                QueryResult(answer = "smart answer", references = listOf(knowledgeItem(1L)), confidence = 0.9f),
            )

            viewModel.onIntent(HybridIntent.SubmitQuery("hello world"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("hello world", state.question)
            assertEquals(sanitized, state.sanitizedQuestion)
            assertEquals("smart answer", state.answer)
            assertEquals(listOf(1L), state.references.map { it.id })
            assertFalse(state.isLoading)
            assertEquals(listOf(sanitized), harness.historyUseCase.smartHistory.value.map { it.query })
        }

    @Test
    fun `submit with mode intent routes to local mode and records local history`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("接地要求是什么")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenReturn(localModeResult(sanitized, listOf(knowledgeItem(7L))))

            viewModel.onIntent(HybridIntent.SubmitQueryWithMode("接地要求是什么", DisplayMode.LOCAL))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(sanitized, state.localSummaryState.query)
            assertEquals(listOf(7L), state.references.map { it.id })
            assertFalse(state.isLoading)
            assertEquals(listOf(sanitized), harness.historyUseCase.localHistory.value.map { it.query })
        }

    @Test
    fun `local mode with no retrievals completes with empty evidence`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("空结果问题")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenReturn(localModeResult(sanitized, emptyList()))

            viewModel.submitQuery("空结果问题", DisplayMode.LOCAL)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(sanitized, state.localSummaryState.query)
            assertTrue(state.references.isEmpty())
            assertTrue(state.evidenceList.isEmpty())
            assertFalse(state.isLoading)
        }

    @Test
    fun `local mode failure falls back without corrupting the submission`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("local failure case")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
                .thenThrow(IllegalStateException("local down"))

            viewModel.submitQuery("local failure case", DisplayMode.LOCAL)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(sanitized, state.localSummaryState.query)
            assertTrue(state.evidenceList.isEmpty())
            // a failed search is a retryable error, not a zero-hit "no match" state
            assertEquals(LocalPageState.ERROR, state.localSummaryState.pageState)
            assertFalse(state.localSummaryState.errorMessage.orEmpty().contains("local down"))
            // the failed attempt still lands in local history (pre-existing behavior)
            assertEquals(listOf(sanitized), harness.historyUseCase.localHistory.value.map { it.query })
        }

    @Test
    fun `gemma streaming callbacks apply in arrival order`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("streaming question")
            val gate = CompletableDeferred<Unit>()
            val result = localModeResult(sanitized, listOf(knowledgeItem(3L)))
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val onResult = invocation.getArgument<(String) -> Unit>(3)
                onResult("first chunk")
                gate.await()
                onResult("final chunk")
                result
            }

            viewModel.submitQuery("streaming question", DisplayMode.LOCAL)
            advanceUntilIdle()
            assertEquals("first chunk", viewModel.uiState.value.aiAnswer)

            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("final chunk", state.aiAnswer)
            assertEquals(sanitized, state.localSummaryState.query)
            assertFalse(state.isLoading)
        }

    @Test
    fun `submit with mode intent routes to ai mode`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.aiMode("ai question", webSearchEnabled = false))
                .thenReturn("replied by ai")

            viewModel.onIntent(HybridIntent.SubmitQueryWithMode("ai question", DisplayMode.AI))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("replied by ai", state.answer)
            assertTrue(state.references.isEmpty())
            assertFalse(state.isLoading)
            Mockito.verify(harness.queryUseCase).aiMode("ai question", webSearchEnabled = false)
        }

    @Test
    fun `ai mode failure maps to error answer text`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            whenever(harness.queryUseCase.aiMode(any(), any()))
                .thenThrow(IllegalStateException("ai down"))

            viewModel.submitQuery("ai question", DisplayMode.AI)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.answer.startsWith("AI error:"))
            assertTrue(state.answer.contains("ai down"))
            assertFalse(state.isLoading)
        }

    @Test
    fun `smart mode failure maps to fallback answer and still records history`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("smart boom")
            val fts = HybridUtils.normalizeForFts(sanitized)
            whenever(harness.queryUseCase.invoke(fts))
                .thenThrow(IllegalStateException("smart down"))

            viewModel.submitQuery("smart boom", DisplayMode.SMART)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.answer.startsWith("AI error:"))
            assertTrue(state.answer.contains("smart down"))
            assertFalse(state.isLoading)
            assertEquals(listOf(sanitized), harness.historyUseCase.smartHistory.value.map { it.query })
        }

    @Test
    fun `side effect initialization runs history init before asset import`() {
        runHybridTest(
            factory = {
                HybridVmHarness(historyOverride = Mockito.spy(fakeHistoryUseCase()))
            },
        ) { harness ->
            val viewModel = harness.create(this)
            advanceUntilIdle()

            val inOrder = Mockito.inOrder(harness.historyUseCase, harness.importer)
            inOrder.verify(harness.historyUseCase).init()
            inOrder.verify(harness.importer).importAssetsIfNeed()
            // history flows are forwarded into state
            assertEquals(harness.historyUseCase.localHistory.value, viewModel.uiState.value.localSearchHistory)
        }
    }
}
