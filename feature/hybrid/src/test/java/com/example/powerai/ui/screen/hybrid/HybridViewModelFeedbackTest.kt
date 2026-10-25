package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Characterization tests for [HybridViewModel] local answer feedback:
 * positive/negative toggles, duplicate handling, blank-query guard,
 * persistence sync on submission and repository failure semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelFeedbackTest {
    private suspend fun kotlinx.coroutines.test.TestScope.submitLocal(
        harness: HybridVmHarness,
        viewModel: HybridViewModel,
        question: String,
    ) {
        val sanitized = HybridUtils.sanitizeQuestion(question)
        whenever(harness.queryUseCase.localMode(any(), any(), any(), any()))
            .thenReturn(localModeResult(sanitized))
        viewModel.submitQuery(question, DisplayMode.LOCAL)
        advanceUntilIdle()
    }

    @Test
    fun `thumb up toggles between none and up`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            submitLocal(harness, viewModel, "thumb up question")
            val query = HybridUtils.sanitizeQuestion("thumb up question")
            assertEquals(LocalAnswerFeedback.NONE, viewModel.uiState.value.localAnswerFeedback)

            viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbUp)
            assertEquals(LocalAnswerFeedback.UP, viewModel.uiState.value.localAnswerFeedback)
            assertEquals(LocalAnswerFeedback.UP, harness.feedbackStore.getFeedback(query))

            viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbUp)
            assertEquals(LocalAnswerFeedback.NONE, viewModel.uiState.value.localAnswerFeedback)
            assertEquals(LocalAnswerFeedback.NONE, harness.feedbackStore.getFeedback(query))
        }

    @Test
    fun `thumb down toggles between none and down`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)
            submitLocal(harness, viewModel, "thumb down question")
            val query = HybridUtils.sanitizeQuestion("thumb down question")

            viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbDown)
            assertEquals(LocalAnswerFeedback.DOWN, viewModel.uiState.value.localAnswerFeedback)
            assertEquals(LocalAnswerFeedback.DOWN, harness.feedbackStore.getFeedback(query))

            viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbDown)
            assertEquals(LocalAnswerFeedback.NONE, viewModel.uiState.value.localAnswerFeedback)
            assertEquals(LocalAnswerFeedback.NONE, harness.feedbackStore.getFeedback(query))
        }

    @Test
    fun `feedback toggle is ignored while the query is blank`() =
        runHybridTest { harness ->
            val viewModel = harness.create(this)

            viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbUp)
            viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbDown)

            assertEquals(LocalAnswerFeedback.NONE, viewModel.uiState.value.localAnswerFeedback)
        }

    @Test
    fun `submission syncs persisted feedback for the query`() {
        runHybridTest(
            factory = {
                HybridVmHarness(
                    feedbackStore =
                        fakeFeedbackStore().also {
                            it.setFeedback(HybridUtils.sanitizeQuestion("known query"), LocalAnswerFeedback.UP)
                        },
                )
            },
        ) { harness ->
            val viewModel = harness.create(this)
            submitLocal(harness, viewModel, "known query")

            assertEquals(LocalAnswerFeedback.UP, viewModel.uiState.value.localAnswerFeedback)
        }
    }

    @Test
    fun `feedback repository failure surfaces and state stays untouched`() {
        val failingStore = mock<com.example.powerai.data.settings.LocalAnswerFeedbackStore>()
        runHybridTest(factory = { HybridVmHarness(feedbackStore = failingStore) }) { harness ->
            whenever(failingStore.getFeedback(any())).thenReturn(LocalAnswerFeedback.NONE)
            doThrow(IllegalStateException("prefs down"))
                .whenever(failingStore)
                .setFeedback(any(), any())
            val viewModel = harness.create(this)
            submitLocal(harness, viewModel, "failure question")

            val error =
                assertThrows(IllegalStateException::class.java) {
                    viewModel.onIntent(HybridIntent.ToggleLocalAnswerThumbUp)
                }
            assertEquals("prefs down", error.message)
            assertEquals(LocalAnswerFeedback.NONE, viewModel.uiState.value.localAnswerFeedback)
        }
    }
}
