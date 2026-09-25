package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.repository.ChatHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionManagerTest {
    private lateinit var repo: FakeChatHistoryRepository
    private lateinit var manager: ChatSessionManager

    private class FakeChatHistoryRepository : ChatHistoryRepository {
        var lastSaved: ChatHistorySnapshot? = null
        private var stored: ChatHistorySnapshot? = null
        override suspend fun loadHistory(): ChatHistorySnapshot =
            stored ?: ChatHistorySnapshot(sessions = emptyList(), selectedSessionId = null, currentSessionId = null)
        override suspend fun saveHistory(snapshot: ChatHistorySnapshot) {
            stored = snapshot
            lastSaved = snapshot
        }
        override suspend fun clearHistory() {
            stored = null
        }
    }

    @Before
    fun setup() {
        repo = FakeChatHistoryRepository()
        manager = ChatSessionManager(repo)
    }

    @Test
    fun `create new session prepends and returns id`() {
        val existing = listOf(
            ChatSession(id = 1, title = "t1", turns = emptyList(), createdAtMillis = 0, updatedAtMillis = 0)
        )
        val (newList, newId) = manager.createNewSession(existing)
        assertEquals(2, newList.size)
        assertEquals(newList.first().id, newId)
        assertEquals("新对", newList.first().title)
    }

    @Test
    fun `append turn with null session creates new session`() {
        val (updated, sid) = manager.appendTurn(emptyList(), null, 42, "q")
        assertEquals(1, updated.size)
        assertEquals(sid, updated.first().id)
        assertEquals("q", updated.first().turns.first().question)
    }

    @Test
    fun `append turn to existing updates title if was new`() {
        val base = ChatSession(id = 1, title = "新对", turns = emptyList(), createdAtMillis = 0, updatedAtMillis = 0)
        val (updated, sid) = manager.appendTurn(listOf(base), 1, 2, "hello")
        assertEquals(1, updated.size)
        assertEquals("hello", updated.first().title)
        assertEquals(sid, 1)
    }

    @Test
    fun `update turn answer sets isError`() {
        val turn = ChatTurn(1, "q", "", askedAtMillis = 0)
        val session = ChatSession(id = 1, title = "", turns = listOf(turn), createdAtMillis = 0, updatedAtMillis = 0)
        val result = manager.updateTurnAnswer(listOf(session), 1, 1, "a", true)
        assertEquals("a", result.first().turns.first().answer)
        assertEquals(true, result.first().turns.first().isError)
    }

    @Test
    fun `persist and load history delegate to store`() = runTest {
        val snap = ChatHistorySnapshot(sessions = emptyList(), selectedSessionId = null, currentSessionId = null)
        manager.persistHistory(snap.sessions, snap.selectedSessionId, snap.currentSessionId)
        assertEquals(snap, repo.lastSaved)
        val loaded = manager.loadHistory()
        assertEquals(snap, loaded)
    }
}
