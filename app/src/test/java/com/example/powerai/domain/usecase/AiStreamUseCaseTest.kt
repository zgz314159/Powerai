package com.example.powerai.domain.usecase

import com.example.powerai.core.model.util.Cancellable
import com.example.powerai.core.repository.AiStreamingRepository
import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.domain.model.chat.AiStreamState
import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.repository.ChatHistoryRepository
import com.example.powerai.domain.repository.WebSearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiStreamUseCaseTest {
    private lateinit var webSearchRepository: WebSearchRepository
    private lateinit var chatHistoryRepository: FakeChatHistoryRepository
    private lateinit var sessionManager: ChatSessionManager
    private lateinit var aiStreamingRepository: AiStreamingRepository
    private lateinit var remoteConfigRepository: RemoteConfigRepository
    private lateinit var useCase: AiStreamUseCase

    private class FakeChatHistoryRepository : ChatHistoryRepository {
        var lastSaved: ChatHistorySnapshot? = null
        override suspend fun loadHistory(): ChatHistorySnapshot =
            lastSaved ?: ChatHistorySnapshot(sessions = emptyList(), selectedSessionId = null, currentSessionId = null)
        override suspend fun saveHistory(snapshot: ChatHistorySnapshot) { lastSaved = snapshot }
        override suspend fun clearHistory() { lastSaved = null }
    }

    @Before
    fun setup() {
        webSearchRepository = mock()
        chatHistoryRepository = FakeChatHistoryRepository()
        sessionManager = ChatSessionManager(chatHistoryRepository)
        aiStreamingRepository = mock()
        remoteConfigRepository = mock()
        useCase = AiStreamUseCase(webSearchRepository, sessionManager, aiStreamingRepository, remoteConfigRepository)
    }

    private fun stubRemoteConfig() {
        whenever(remoteConfigRepository.getAiApiKey()).thenReturn("key")
        whenever(remoteConfigRepository.getAiBaseUrl()).thenReturn("https://api")
        whenever(remoteConfigRepository.getDeepSeekModel()).thenReturn("model")
        whenever(remoteConfigRepository.isDebug()).thenReturn(false)
        whenever(remoteConfigRepository.getDebugStreamUrl()).thenReturn(null)
        whenever(webSearchRepository.isConfigured()).thenReturn(false)
    }

    @Test
    fun `createNewSession produces session in front with correct title`() {
        val (list, id) = useCase.sessionManager.createNewSession(emptyList())
        assertEquals(1, list.size)
        val session = list[0]
        assertEquals("新对", session.title)
        assertEquals(id, session.id)
    }

    @Test
    fun `appendTurn creates session when none exists`() {
        val (updated, sid) = useCase.sessionManager.appendTurn(emptyList(), null, turnId = 100L, question = "hi")
        assertEquals(1, updated.size)
        assertEquals(sid, updated[0].id)
        assertEquals(1, updated[0].turns.size)
        assertEquals("hi", updated[0].turns[0].question)
        assertEquals(100L, updated[0].turns[0].id)
    }

    @Test
    fun `appendTurn adds to existing session and updates title if first turn`() {
        val existing = listOf(
            ChatSession(id = 42L, title = "新对", turns = emptyList(), createdAtMillis = 0, updatedAtMillis = 0)
        )
        val (updated, sid) = useCase.sessionManager.appendTurn(existing, 42L, turnId = 200L, question = "question?")
        assertEquals(42L, sid)
        assertEquals(1, updated.size)
        val session = updated[0]
        assertEquals("question?", session.title)
        assertEquals(1, session.turns.size)
        assertEquals("question?", session.turns[0].question)
    }

    @Test
    fun `appendTurn keeps title when not first turn`() {
        val existing = listOf(
            ChatSession(
                id = 99L, title = "foo",
                turns = listOf(ChatTurn(id = 1L, question = "a", answer = "", askedAtMillis = 0)),
                createdAtMillis = 0, updatedAtMillis = 0
            )
        )
        val (updated, sid) = useCase.sessionManager.appendTurn(existing, 99L, turnId = 201L, question = "bar")
        assertEquals(99L, sid)
        val session = updated[0]
        assertEquals("foo", session.title)
        assertEquals(2, session.turns.size)
    }

    @Test
    fun `updateTurnAnswer modifies correct turn`() {
        val turn = ChatTurn(id = 5L, question = "q", answer = "", askedAtMillis = 0)
        val session = ChatSession(id = 11L, title = "", turns = listOf(turn), createdAtMillis = 0, updatedAtMillis = 0)
        val result = useCase.sessionManager.updateTurnAnswer(listOf(session), sessionId = 11L, turnId = 5L, answer = "ans", isError = true)
        assertEquals(1, result.size)
        val updatedTurn = result[0].turns[0]
        assertEquals("ans", updatedTurn.answer)
        assertTrue(updatedTurn.isError)
    }

    @Test
    fun `updateTurnSources updates correct turn`() {
        val turn = ChatTurn(id = 7L, question = "q", answer = "", askedAtMillis = 0)
        val session = ChatSession(id = 22L, title = "", turns = listOf(turn), createdAtMillis = 0, updatedAtMillis = 0)
        val result = useCase.sessionManager.updateTurnSources(listOf(session), sessionId = 22L, turnId = 7L, sources = listOf("a", "b"))
        assertEquals(1, result.size)
        assertEquals(listOf("a", "b"), result[0].turns[0].sources)
    }

    @Test
    fun `selectSessionAndPersist saves snapshot with chosen id`() = runBlocking {
        val sessions = listOf(ChatSession(id = 1L, title = "", turns = emptyList(), createdAtMillis = 0, updatedAtMillis = 0))
        val (outSessions, outSid) = useCase.sessionManager.selectSessionAndPersist(sessions, 555L)
        assertSame(sessions, outSessions)
        assertEquals(555L, outSid)
        val snap = chatHistoryRepository.lastSaved
        assertNotNull(snap)
        assertEquals(555L, snap!!.selectedSessionId)
        assertEquals(555L, snap.currentSessionId)
        assertEquals(sessions, snap.sessions)
    }

    @Test
    fun `createNewSessionAndPersist creates and saves new session`() = runBlocking {
        val (newSessions, newSid) = useCase.sessionManager.createNewSessionAndPersist(emptyList())
        assertEquals(1, newSessions.size)
        assertEquals(newSid, newSessions[0].id)
        val snap = chatHistoryRepository.lastSaved
        assertNotNull(snap)
        assertEquals(newSid, snap!!.selectedSessionId)
        assertEquals(newSid, snap.currentSessionId)
        assertEquals(newSessions, snap.sessions)
    }

    @Test
    fun `updateTurnSourcesAndPersist saves snapshot with same ids`() = runBlocking {
        val sessions = listOf(
            ChatSession(
                id = 10L, title = "",
                turns = listOf(ChatTurn(id = 1L, question = "q", answer = "", askedAtMillis = 0)),
                createdAtMillis = 0, updatedAtMillis = 0
            )
        )
        val result = useCase.sessionManager.updateTurnSourcesAndPersist(sessions, sessionId = 10L, turnId = 1L, sources = listOf("x"))
        assertEquals(listOf("x"), result.first().turns.first().sources)
        val snap = chatHistoryRepository.lastSaved
        assertNotNull(snap)
        assertEquals(10L, snap!!.selectedSessionId)
        assertEquals(10L, snap.currentSessionId)
        assertEquals(result, snap.sessions)
    }

    @Test
    fun `updateSessionsAndPersist persists supplied ids`() = runBlocking {
        val sessions = listOf(ChatSession(id = 20L, title = "", turns = emptyList(), createdAtMillis = 0, updatedAtMillis = 0))
        val out = useCase.sessionManager.updateSessionsAndPersist(sessions, selectedSessionId = 20L, currentSessionId = 30L)
        assertSame(sessions, out)
        val snap = chatHistoryRepository.lastSaved
        assertNotNull(snap)
        assertEquals(20L, snap!!.selectedSessionId)
        assertEquals(30L, snap.currentSessionId)
        assertEquals(sessions, snap.sessions)
    }

    @Test
    fun `askAiStream invokes callbacks with streaming content`() = runBlocking {
        stubRemoteConfig()
        whenever(aiStreamingRepository.buildBody(any(), any(), any())).thenReturn("{}")
        whenever(aiStreamingRepository.isDoneMarker(any())).thenReturn(false)
        whenever(aiStreamingRepository.isDoneMarker("[DONE]")).thenReturn(true)
        whenever(aiStreamingRepository.extractTextChunk(any())).thenReturn("Hi")
        val cancellable = object : Cancellable {
            override fun cancel() {}
        }
        whenever(
            aiStreamingRepository.startStreaming(any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        ).thenAnswer { invocation ->
            val onData = invocation.getArgument<(String) -> Unit>(2)
            val onClosed = invocation.getArgument<(() -> Unit)?>(3)
            onData("{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}")
            onData("[DONE]")
            onClosed?.invoke()
            cancellable
        }

        val loadings = mutableListOf<Boolean>()
        val states = mutableListOf<AiStreamState>()
        var errorMsg: String? = null
        val webSrcs = mutableListOf<List<String>>()
        val source = useCase.askAiStream(
            userInput = "hello",
            webSearchEnabled = false,
            onState = { states.add(it) },
            onLoading = { loadings.add(it) },
            onError = { errorMsg = it },
            onWebSources = { webSrcs.add(it) },
            updateCurrentTurnSources = { _, _ -> },
            getHistoryTurns = { emptyList() },
            updateTurnRetryAllowed = { _, _ -> }
        )
        assertNotNull(source)
        assertTrue(loadings.isNotEmpty())
        assertEquals(true, loadings.first())
        assertTrue(loadings.contains(false))
        assertTrue(states.isNotEmpty())
        assertEquals(AiStreamState.Loading, states.first())
        assertTrue(states.any { it is AiStreamState.Success && it.text.contains("Hi") })
        assertNull(errorMsg)
        assertTrue(useCase.webSources.value.isEmpty())
    }

    @Test
    fun `performStreamRequest buffers and updates sessions`() = runBlocking {
        stubRemoteConfig()
        whenever(aiStreamingRepository.buildBody(any(), any(), any())).thenReturn("{}")
        whenever(aiStreamingRepository.isDoneMarker(any())).thenReturn(false)
        whenever(aiStreamingRepository.isDoneMarker("[DONE]")).thenReturn(true)
        whenever(aiStreamingRepository.extractTextChunk(any())).thenReturn("Hi")
        val cancellable = object : Cancellable {
            override fun cancel() {}
        }
        whenever(
            aiStreamingRepository.startStreaming(any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        ).thenAnswer { invocation ->
            val onData = invocation.getArgument<(String) -> Unit>(2)
            val onClosed = invocation.getArgument<(() -> Unit)?>(3)
            onData("{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}")
            onData("[DONE]")
            onClosed?.invoke()
            cancellable
        }

        val sessionsList = mutableListOf<List<ChatSession>>()
        val loadings = mutableListOf<Boolean>()
        val states = mutableListOf<AiStreamState>()
        var errorMsg: String? = null
        val webSrcs = mutableListOf<List<String>>()

        val turnId = 123L
        val initialSessions = listOf(
            ChatSession(
                id = 999L, title = "foo",
                turns = listOf(ChatTurn(id = turnId, question = "q", answer = "", askedAtMillis = 0)),
                createdAtMillis = 0, updatedAtMillis = 0
            )
        )

        val source = useCase.performStreamRequest(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            sessions = initialSessions,
            currentSessionId = 999L,
            turnId = turnId,
            userInput = "hello",
            webSearchEnabled = false,
            onState = { states.add(it) },
            onLoading = { loadings.add(it) },
            onError = { errorMsg = it },
            onWebSources = { webSrcs.add(it) },
            updateCurrentTurnSources = { _, _ -> },
            getHistoryTurns = { emptyList() },
            updateTurnRetryAllowed = { _, _ -> },
            updateSessions = { sessionsList.add(it) }
        )

        assertNotNull(source)
        assertTrue(loadings.isNotEmpty())
        assertEquals(true, loadings.first())
        assertTrue(loadings.contains(false))
        assertTrue(states.isNotEmpty())
        assertEquals(AiStreamState.Loading, states.first())
        assertTrue(states.any { it is AiStreamState.Success && it.text.contains("Hi") })
        assertNull(errorMsg)
        assertTrue(useCase.webSources.value.isEmpty())
        kotlinx.coroutines.delay(600)
        assertTrue(sessionsList.isNotEmpty())
    }
}
