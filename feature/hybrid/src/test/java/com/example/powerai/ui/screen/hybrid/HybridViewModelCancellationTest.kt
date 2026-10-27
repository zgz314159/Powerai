package com.example.powerai.ui.screen.hybrid

import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.QueryResult
import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

/**
 * Cancellation semantics for [HybridViewModel]: a newest submit cancels the
 * in-flight request (R06/R07 newest-request-wins) and a cancelled scope never
 * publishes fallback state. Fully virtual-scheduler driven.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelCancellationTest {
    @Test
    fun `newest ai submit cancels the in-flight request deterministically`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var firstCall = true
            var firstCancelled = false
            whenever(harness.queryUseCase.aiMode(any(), any())).doSuspendableAnswer {
                if (firstCall) {
                    firstCall = false
                    try {
                        gate.await()
                    } catch (error: CancellationException) {
                        firstCancelled = true
                        throw error
                    }
                    "stale answer"
                } else {
                    "fresh answer"
                }
            }

            viewModel.submitQuery("first question", DisplayMode.AI)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isLoading)

            viewModel.submitQuery("second question", DisplayMode.AI)
            advanceUntilIdle()

            assertTrue(firstCancelled)
            val state = viewModel.uiState.value
            assertEquals("fresh answer", state.answer)
            assertEquals("second question", state.question)
            assertFalse(state.isLoading)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals("fresh answer", viewModel.uiState.value.answer)
        }

    @Test
    fun `newest smart submit cancels the in-flight request deterministically`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var firstCall = true
            var firstCancelled = false
            whenever(harness.queryUseCase.invoke(any())).doSuspendableAnswer {
                if (firstCall) {
                    firstCall = false
                    try {
                        gate.await()
                    } catch (error: CancellationException) {
                        firstCancelled = true
                        throw error
                    }
                    QueryResult(answer = "stale smart", references = emptyList(), confidence = 0f)
                } else {
                    QueryResult(answer = "fresh smart", references = listOf(knowledgeItem(9L)), confidence = 1f)
                }
            }

            viewModel.submitQuery("first smart", DisplayMode.SMART)
            advanceUntilIdle()
            viewModel.submitQuery("second smart", DisplayMode.SMART)
            advanceUntilIdle()

            assertTrue(firstCancelled)
            val state = viewModel.uiState.value
            assertEquals("fresh smart", state.answer)
            assertEquals(listOf(9L), state.references.map { it.id })
            assertFalse(state.isLoading)
            // the cancelled request never reached history recording
            val expectedSecond = HybridUtils.sanitizeQuestion("second smart")
            assertEquals(listOf(expectedSecond), harness.historyUseCase.smartHistory.value.map { it.query })

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals("fresh smart", viewModel.uiState.value.answer)
        }

    @Test
    fun `newest local submit cancels the in-flight request deterministically`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            var firstCall = true
            var firstCancelled = false
            val secondSanitized = HybridUtils.sanitizeQuestion("second local")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer {
                if (firstCall) {
                    firstCall = false
                    try {
                        gate.await()
                    } catch (error: CancellationException) {
                        firstCancelled = true
                        throw error
                    }
                    localModeResult(HybridUtils.sanitizeQuestion("first local"))
                } else {
                    localModeResult(secondSanitized, listOf(knowledgeItem(2L)))
                }
            }

            viewModel.submitQuery("first local", DisplayMode.LOCAL)
            advanceUntilIdle()
            viewModel.submitQuery("second local", DisplayMode.LOCAL)
            advanceUntilIdle()

            assertTrue(firstCancelled)
            val state = viewModel.uiState.value
            assertEquals(secondSanitized, state.localSummaryState.query)
            assertEquals(listOf(2L), state.references.map { it.id })
            assertFalse(state.isLoading)
            assertEquals(listOf(secondSanitized), harness.historyUseCase.localHistory.value.map { it.query })

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(secondSanitized, viewModel.uiState.value.localSummaryState.query)
            assertEquals(listOf(secondSanitized), harness.historyUseCase.localHistory.value.map { it.query })
        }

    @Test
    fun `scope cancellation mid flight skips the fallback write`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            val gate = CompletableDeferred<Unit>()
            val sanitized = HybridUtils.sanitizeQuestion("cancelled local")
            whenever(harness.queryUseCase.localMode(any(), any(), any(), any())).doSuspendableAnswer {
                gate.await()
                localModeResult(sanitized)
            }

            viewModel.submitQuery("cancelled local", DisplayMode.LOCAL)
            advanceUntilIdle()

            viewModel.viewModelScope.cancel()
            gate.complete(Unit)
            advanceUntilIdle()

            // cancellation propagates instead of mapping to the local fallback
            val state = viewModel.uiState.value
            assertTrue(state.isLoading)
            assertEquals(sanitized, state.localSummaryState.query)
            assertTrue(harness.historyUseCase.localHistory.value.isEmpty())
        }
}
