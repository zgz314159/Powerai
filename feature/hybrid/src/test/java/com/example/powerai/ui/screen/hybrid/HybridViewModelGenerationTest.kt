package com.example.powerai.ui.screen.hybrid

import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.QueryResult
import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

/**
 * Generation isolation for [HybridViewModel]: each submit issues a unique
 * request token so superseded generations — including gemma callback child
 * jobs — can never write payload, streaming content, history, loading/result
 * state or fallback errors. Pure virtual-scheduler determinism.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelGenerationTest {
    @Test
    fun `same query resubmit ignores the previous generation streaming callback`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("repeat query")
            var calls = 0
            var firstCallback: ((String) -> Unit)? = null
            var secondCallback: ((String) -> Unit)? = null
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val onResult = invocation.getArgument<(String) -> Unit>(3)
                calls += 1
                if (calls == 1) {
                    firstCallback = onResult
                    localModeResult(sanitized, listOf(knowledgeItem(1L)), gemmaJob = Job())
                } else {
                    secondCallback = onResult
                    localModeResult(sanitized, listOf(knowledgeItem(2L)))
                }
            }

            viewModel.submitQuery("repeat query", DisplayMode.LOCAL)
            advanceUntilIdle()
            assertEquals(listOf(1L), viewModel.uiState.value.references.map { it.id })

            viewModel.submitQuery("repeat query", DisplayMode.LOCAL)
            advanceUntilIdle()
            val afterSecond = viewModel.uiState.value
            assertEquals(listOf(2L), afterSecond.references.map { it.id })
            assertEquals(sanitized, afterSecond.localSummaryState.query)

            // identical query strings: the stale generation's delayed gemma
            // callback must be rejected by the request token, not by the query
            val summaryBeforeStale = afterSecond.localSummaryState
            firstCallback!!.invoke("stale chunk")
            advanceUntilIdle()
            val afterStale = viewModel.uiState.value
            assertNull(afterStale.aiAnswer)
            assertEquals(summaryBeforeStale, afterStale.localSummaryState)
            assertEquals(listOf(2L), afterStale.references.map { it.id })

            // the current generation may still stream
            secondCallback!!.invoke("fresh chunk")
            advanceUntilIdle()
            assertEquals("fresh chunk", viewModel.uiState.value.aiAnswer)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `different query supersede drops the first generation payload`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var firstCancelled = false
            var calls = 0
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer {
                calls += 1
                if (calls == 1) {
                    try {
                        gate.await()
                    } catch (error: CancellationException) {
                        firstCancelled = true
                        throw error
                    }
                    localModeResult(HybridUtils.sanitizeQuestion("first local"), listOf(knowledgeItem(1L)))
                } else {
                    localModeResult(HybridUtils.sanitizeQuestion("second local"), listOf(knowledgeItem(2L)))
                }
            }

            viewModel.submitQuery("first local", DisplayMode.LOCAL)
            advanceUntilIdle()
            viewModel.submitQuery("second local", DisplayMode.LOCAL)
            advanceUntilIdle()

            assertTrue(firstCancelled)
            val state = viewModel.uiState.value
            assertEquals(HybridUtils.sanitizeQuestion("second local"), state.localSummaryState.query)
            assertEquals(listOf(2L), state.references.map { it.id })
            assertFalse(state.isLoading)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(2L), viewModel.uiState.value.references.map { it.id })
        }

    @Test
    fun `first generation writes no history when superseded`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            whenever(harness.queryUseCase.invoke(any())).doSuspendableAnswer {
                calls += 1
                if (calls == 1) {
                    gate.await()
                    QueryResult(answer = "stale smart", references = emptyList(), confidence = 0f)
                } else {
                    QueryResult(answer = "fresh smart", references = emptyList(), confidence = 1f)
                }
            }

            viewModel.submitQuery("first smart", DisplayMode.SMART)
            advanceUntilIdle()
            viewModel.submitQuery("second smart", DisplayMode.SMART)
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val expected = HybridUtils.sanitizeQuestion("second smart")
            assertEquals(listOf(expected), harness.historyUseCase.smartHistory.value.map { it.query })
            assertEquals("fresh smart", viewModel.uiState.value.answer)
        }

    @Test
    fun `only the second generation can complete`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            whenever(harness.queryUseCase.aiMode(any(), any())).doSuspendableAnswer {
                calls += 1
                if (calls == 1) {
                    gate.await()
                    "stale answer"
                } else {
                    "fresh answer"
                }
            }

            viewModel.submitQuery("first ai", DisplayMode.AI)
            advanceUntilIdle()
            viewModel.submitQuery("second ai", DisplayMode.AI)
            advanceUntilIdle()

            assertEquals("fresh answer", viewModel.uiState.value.answer)
            assertFalse(viewModel.uiState.value.isLoading)

            // releasing the stale request cannot flip the completion
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals("fresh answer", viewModel.uiState.value.answer)
            assertEquals("second ai", viewModel.uiState.value.question)
        }

    @Test
    fun `superseded generation never emits a fallback error`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            whenever(harness.queryUseCase.aiMode(any(), any())).doSuspendableAnswer {
                calls += 1
                if (calls == 1) {
                    gate.await()
                    throw IllegalStateException("stale boom")
                } else {
                    "fresh answer"
                }
            }

            viewModel.submitQuery("gen one", DisplayMode.AI)
            advanceUntilIdle()
            viewModel.submitQuery("gen two", DisplayMode.AI)
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("fresh answer", state.answer)
            assertFalse(state.answer.contains("stale boom"))
            assertFalse(state.answer.startsWith("AI error:"))
            assertFalse(state.isLoading)
        }

    @Test
    fun `scope cancellation invalidates every generation`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val sanitized = HybridUtils.sanitizeQuestion("frozen query")
            var captured: ((String) -> Unit)? = null
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                captured = invocation.getArgument<(String) -> Unit>(3)
                localModeResult(sanitized, listOf(knowledgeItem(1L)))
            }

            viewModel.submitQuery("frozen query", DisplayMode.LOCAL)
            advanceUntilIdle()
            assertEquals(listOf(1L), viewModel.uiState.value.references.map { it.id })
            val frozen = viewModel.uiState.value

            viewModel.viewModelScope.cancel()
            captured!!.invoke("post cancel chunk")
            advanceUntilIdle()

            val after = viewModel.uiState.value
            assertNull(after.aiAnswer)
            assertEquals(frozen.localSummaryState, after.localSummaryState)
            assertEquals(frozen.references, after.references)
            assertEquals(frozen.isLoading, after.isLoading)
        }
}
