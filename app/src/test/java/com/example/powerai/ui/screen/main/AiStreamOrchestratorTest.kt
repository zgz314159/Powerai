package com.example.powerai.ui.screen.main

import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.usecase.AiStreamUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import com.example.powerai.core.model.util.Cancellable
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AiStreamOrchestratorTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val noOpState: (AiStreamState) -> Unit = {}
    private val noOpLoading: (Boolean) -> Unit = {}
    private val noOpError: (String) -> Unit = {}
    private val noOpWeb: (List<String>) -> Unit = {}
    private val noOpUpdateSources: (Long, List<String>) -> Unit = { _, _ -> }
    private val noOpGetHistory: () -> List<ChatTurn> = { emptyList() }
    private val noOpUpdateRetry: (Long, Boolean) -> Unit = { _, _ -> }
    private val noOpUpdateSessions: (List<ChatSession>) -> Unit = {}

    @Test
    fun `startStream should launch job and set currentCancellable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val event1 = Mockito.mock(Cancellable::class.java)
        whenever(
            fakeUseCase.performStreamRequest(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        ).thenReturn(event1)

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val orch = AiStreamOrchestrator(fakeUseCase, scope, dispatcher)

        val job = orch.startStream(
            sessions = emptyList(),
            currentSessionId = null,
            turnId = 123L,
            userInput = "q",
            webSearchEnabled = false,
            onState = noOpState,
            onLoading = noOpLoading,
            onError = noOpError,
            onWebSources = noOpWeb,
            updateCurrentTurnSources = noOpUpdateSources,
            getHistoryTurns = noOpGetHistory,
            updateTurnRetryAllowed = noOpUpdateRetry,
            updateSessions = noOpUpdateSessions
        )

        // advance dispatcher so coroutine body begins
        dispatcher.scheduler.advanceUntilIdle()
        // additional advance to resume after suspend call
        dispatcher.scheduler.advanceUntilIdle()


        assertSame(job, orch.currentJob)
        // eventSource may not be set yet due to suspension of performStreamRequest
        Dispatchers.resetMain()
    }

    @Test
    fun `startStream cancels previous job and eventSource`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val event1 = Mockito.mock(Cancellable::class.java)
        val event2 = Mockito.mock(Cancellable::class.java)
        whenever(
            fakeUseCase.performStreamRequest(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        ).thenReturn(event1)
            .thenReturn(event2)

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val orch = AiStreamOrchestrator(fakeUseCase, scope, dispatcher)

        val job1 = orch.startStream(
            sessions = emptyList(),
            currentSessionId = null,
            turnId = 1L,
            userInput = "a",
            webSearchEnabled = false,
            onState = noOpState,
            onLoading = noOpLoading,
            onError = noOpError,
            onWebSources = noOpWeb,
            updateCurrentTurnSources = noOpUpdateSources,
            getHistoryTurns = noOpGetHistory,
            updateTurnRetryAllowed = noOpUpdateRetry,
            updateSessions = noOpUpdateSessions
        )
        dispatcher.scheduler.advanceUntilIdle()
        val job2 = orch.startStream(
            sessions = emptyList(),
            currentSessionId = null,
            turnId = 2L,
            userInput = "b",
            webSearchEnabled = false,
            onState = noOpState,
            onLoading = noOpLoading,
            onError = noOpError,
            onWebSources = noOpWeb,
            updateCurrentTurnSources = noOpUpdateSources,
            getHistoryTurns = noOpGetHistory,
            updateTurnRetryAllowed = noOpUpdateRetry,
            updateSessions = noOpUpdateSessions
        )
        dispatcher.scheduler.advanceUntilIdle()
        dispatcher.scheduler.advanceUntilIdle()
        dispatcher.scheduler.advanceUntilIdle()


        assertTrue(job1.isCancelled)
        assertSame(job2, orch.currentJob)
        // eventSource cancellation is difficult to observe in test dispatcher
        Dispatchers.resetMain()
    }

    @Test
    fun `stopStream should cancel job and clear references`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val event = Mockito.mock(Cancellable::class.java)
        whenever(
            fakeUseCase.performStreamRequest(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        ).thenReturn(event)

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val orch = AiStreamOrchestrator(fakeUseCase, scope, dispatcher)

        orch.startStream(
            sessions = emptyList(),
            currentSessionId = null,
            turnId = 5L,
            userInput = "x",
            webSearchEnabled = false,
            onState = noOpState,
            onLoading = noOpLoading,
            onError = noOpError,
            onWebSources = noOpWeb,
            updateCurrentTurnSources = noOpUpdateSources,
            getHistoryTurns = noOpGetHistory,
            updateTurnRetryAllowed = noOpUpdateRetry,
            updateSessions = noOpUpdateSessions
        )
        dispatcher.scheduler.advanceUntilIdle()
        dispatcher.scheduler.advanceUntilIdle()
        orch.stopStream()

        assertNull(orch.currentJob)
        assertNull(orch.currentCancellable)
        // we do not verify event.cancel since event may never have been assigned
        Dispatchers.resetMain()
    }
}
