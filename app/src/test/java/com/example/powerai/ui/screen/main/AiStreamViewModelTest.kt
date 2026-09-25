package com.example.powerai.ui.screen.main

import com.example.powerai.data.chat.ChatHistoryStore
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.usecase.AiStreamUseCase
import com.example.powerai.domain.usecase.ChatSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiStreamViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `initialization loads history and sets retry flags`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fakeHistory = Mockito.mock(ChatHistoryStore::class.java)
        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val fakeSessionManager = Mockito.mock(ChatSessionManager::class.java)
        whenever(fakeUseCase.sessionManager).thenReturn(fakeSessionManager)
        whenever(fakeUseCase.webSources).thenReturn(MutableStateFlow(emptyList()))
        val session = ChatSession(
            id = 1L, title = "t",
            turns = listOf(ChatTurn(id = 10L, question = "q", answer = "", askedAtMillis = 0)),
            createdAtMillis = 0, updatedAtMillis = 0
        )
        val snap = com.example.powerai.domain.model.chat.ChatHistorySnapshot(sessions = listOf(session), selectedSessionId = 1L, currentSessionId = 1L)
        whenever(fakeSessionManager.loadHistory()).thenReturn(snap)

        val vm = AiStreamViewModel(fakeHistory, fakeUseCase)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(session), vm.uiState.value.sessions)
        assertEquals(1L, vm.uiState.value.selectedSessionId)
        assertEquals(1L, vm.uiState.value.currentSessionId)
        assertTrue(vm.uiState.value.turnRetryAllowed.containsKey(10L))
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `askAiStream should delegate to orchestrator with correct parameters`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val fakeHistory = Mockito.mock(ChatHistoryStore::class.java)
        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val fakeSessionManager = Mockito.mock(ChatSessionManager::class.java)
        whenever(fakeUseCase.sessionManager).thenReturn(fakeSessionManager)
        whenever(fakeUseCase.webSources).thenReturn(MutableStateFlow(emptyList()))
        whenever(
            fakeSessionManager.appendTurn(any(), anyOrNull(), any(), any())
        ).thenReturn(
            Pair(
                listOf(
                    ChatSession(
                        id = 1L, title = "t", turns = emptyList(),
                        createdAtMillis = 0L, updatedAtMillis = 0L
                    )
                ),
                1L
            )
        )
        whenever(fakeSessionManager.persistHistory(any(), anyOrNull(), anyOrNull())).thenReturn(Unit)
        whenever(
            fakeSessionManager.updateTurnSourcesAndPersist(any(), anyOrNull(), any(), any())
        ).thenReturn(emptyList())
        whenever(
            fakeSessionManager.updateSessionsAndPersist(any(), anyOrNull(), anyOrNull())
        ).thenReturn(emptyList())

        val fakeOrch = Mockito.mock(AiStreamOrchestrator::class.java)
        whenever(
            fakeOrch.startStream(
                any(), anyOrNull(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
            )
        ).thenAnswer { invocation ->
            val updateSources = invocation.getArgument<(Long, List<String>) -> Unit>(9)
            val updateSessions = invocation.getArgument<(List<ChatSession>) -> Unit>(12)
            updateSources(123L, listOf("a", "b"))
            updateSessions(emptyList())
            Job()
        }

        val vm = AiStreamViewModel(
            chatHistoryStore = fakeHistory,
            streamUseCase = fakeUseCase
        )
        vm.orchestrator = fakeOrch

        vm.onIntent(AiStreamIntent.AskAiStream("question", webSearchEnabled = true))
        testScheduler.advanceUntilIdle()

        verify(fakeOrch).startStream(
            any(), anyOrNull(), any(), any(),
            eq(true), any(), any(), any(),
            any(), any(), any(), any(), any()
        )
        assertEquals(emptyList<ChatSession>(), vm.uiState.value.sessions)
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stopStream should delegate to orchestrator and reset loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        whenever(fakeUseCase.sessionManager).thenReturn(Mockito.mock(ChatSessionManager::class.java))
        whenever(fakeUseCase.webSources).thenReturn(MutableStateFlow(emptyList()))
        val fakeHistory = Mockito.mock(ChatHistoryStore::class.java)
        val fakeOrch = Mockito.mock(AiStreamOrchestrator::class.java)
        val vm = AiStreamViewModel(
            chatHistoryStore = fakeHistory,
            streamUseCase = fakeUseCase
        )
        vm.orchestrator = fakeOrch

        vm.onIntent(AiStreamIntent.StopStream)
        verify(fakeOrch).stopStream()
        assertFalse(vm.uiState.value.isLoading)
        Dispatchers.resetMain()
    }

    @Test
    fun `selectSession updates ids and delegates persistence`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val fakeSessionManager = Mockito.mock(ChatSessionManager::class.java)
        whenever(fakeUseCase.sessionManager).thenReturn(fakeSessionManager)
        whenever(fakeUseCase.webSources).thenReturn(MutableStateFlow(emptyList()))
        val fakeHistory = Mockito.mock(ChatHistoryStore::class.java)
        val vm = AiStreamViewModel(
            chatHistoryStore = fakeHistory,
            streamUseCase = fakeUseCase
        )

        vm.onIntent(AiStreamIntent.SelectSession(42L))
        testScheduler.advanceUntilIdle()
        assertEquals(42L, vm.uiState.value.selectedSessionId)
        assertEquals(42L, vm.uiState.value.currentSessionId)
        verify(fakeSessionManager).selectSessionAndPersist(any(), eq(42L))
        Dispatchers.resetMain()
    }

    @Test
    fun `newSession should delegate to useCase helper`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fakeUseCase = Mockito.mock(AiStreamUseCase::class.java)
        val fakeSessionManager = Mockito.mock(ChatSessionManager::class.java)
        whenever(fakeUseCase.sessionManager).thenReturn(fakeSessionManager)
        whenever(fakeUseCase.webSources).thenReturn(MutableStateFlow(emptyList()))
        val fakeHistory = Mockito.mock(ChatHistoryStore::class.java)
        whenever(fakeSessionManager.createNewSessionAndPersist(any())).thenReturn(Pair(emptyList(), 99L))
        val vm = AiStreamViewModel(
            chatHistoryStore = fakeHistory,
            streamUseCase = fakeUseCase
        )

        vm.onIntent(AiStreamIntent.NewSession)
        testScheduler.advanceUntilIdle()
        verify(fakeSessionManager).createNewSessionAndPersist(any())
        assertEquals(99L, vm.uiState.value.selectedSessionId)
        assertEquals(99L, vm.uiState.value.currentSessionId)
        Dispatchers.resetMain()
    }
}
