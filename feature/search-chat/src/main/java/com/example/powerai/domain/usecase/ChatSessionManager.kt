package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.repository.ChatHistoryRepository
import javax.inject.Inject

/**
 * Encapsulates operations on chat sessions and turns, including persistence.
 * Splitting this out from [AiStreamUseCase] keeps the streaming use case focused
 * on network behaviour and simplifies unit testing of chat-history logic.
 */
class ChatSessionManager @Inject constructor(private val chatHistoryRepository: ChatHistoryRepository) {
    /**
     * Create a fresh chat session and prepend to existing list. Returns updated
     * list and the id of the new session.
     */
    fun createNewSession(existing: List<ChatSession>): Pair<List<ChatSession>, Long> {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = now,
            title = "新对",
            turns = emptyList(),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val updated = (listOf(session) + existing).take(50)
        return updated to session.id
    }

    fun appendTurn(
        sessions: List<ChatSession>,
        currentSessionId: Long?,
        turnId: Long,
        question: String
    ): Pair<List<ChatSession>, Long> {
        var sid = currentSessionId
        var updated = sessions
        val sessionMissing = sid != null && updated.none { it.id == sid }
        if (sid == null || sessionMissing) {
            val (newList, newSid) = createNewSession(updated)
            updated = newList
            sid = newSid
        }
        val now = System.currentTimeMillis()
        val turn = ChatTurn(id = turnId, question = question, answer = "", askedAtMillis = now)
        updated = updated.map { s ->
            if (s.id != sid) s
            else {
                val wasEmpty = s.turns.isEmpty()
                val newTitle = if (wasEmpty && s.title == "新对") question else s.title
                s.copy(
                    title = newTitle,
                    turns = (s.turns + turn).takeLast(200),
                    updatedAtMillis = now
                )
            }
        }
        val resSid = requireNotNull(sid) { "session id unexpectedly null" }
        return updated to resSid
    }

    suspend fun appendTurnAndPersist(
        sessions: List<ChatSession>,
        currentSessionId: Long?,
        turnId: Long,
        question: String
    ): Pair<List<ChatSession>, Long> {
        val (updated, sid) = appendTurn(sessions, currentSessionId, turnId, question)
        persistHistory(updated, selectedSessionId = sid, currentSessionId = sid)
        return updated to sid
    }

    suspend fun selectSessionAndPersist(
        sessions: List<ChatSession>,
        sessionId: Long
    ): Pair<List<ChatSession>, Long> {
        // no manipulation required aside from tracking the selected/current id
        persistHistory(sessions, selectedSessionId = sessionId, currentSessionId = sessionId)
        return sessions to sessionId
    }

    suspend fun createNewSessionAndPersist(
        sessions: List<ChatSession>
    ): Pair<List<ChatSession>, Long> {
        val (newSessions, newSid) = createNewSession(sessions)
        persistHistory(newSessions, selectedSessionId = newSid, currentSessionId = newSid)
        return newSessions to newSid
    }

    fun updateTurnAnswer(
        sessions: List<ChatSession>,
        sessionId: Long?,
        turnId: Long,
        answer: String,
        isError: Boolean
    ): List<ChatSession> {
        val sid = sessionId ?: return sessions
        val now = System.currentTimeMillis()
        return sessions.map { s ->
            if (s.id != sid) return@map s
            s.copy(
                turns = s.turns.map { t ->
                    if (t.id == turnId) t.copy(answer = answer, isError = isError) else t
                },
                updatedAtMillis = now
            )
        }
    }

    fun updateTurnSources(
        sessions: List<ChatSession>,
        sessionId: Long?,
        turnId: Long,
        sources: List<String>
    ): List<ChatSession> {
        val sid = sessionId ?: return sessions
        val now = System.currentTimeMillis()
        return sessions.map { s ->
            if (s.id != sid) return@map s
            s.copy(
                turns = s.turns.map { t ->
                    if (t.id == turnId) t.copy(sources = sources) else t
                },
                updatedAtMillis = now
            )
        }
    }

    suspend fun updateTurnSourcesAndPersist(
        sessions: List<ChatSession>,
        sessionId: Long?,
        turnId: Long,
        sources: List<String>
    ): List<ChatSession> {
        val updated = updateTurnSources(sessions, sessionId, turnId, sources)
        persistHistory(updated, selectedSessionId = sessionId, currentSessionId = sessionId)
        return updated
    }

    suspend fun updateSessionsAndPersist(
        sessions: List<ChatSession>,
        selectedSessionId: Long?,
        currentSessionId: Long?
    ): List<ChatSession> {
        persistHistory(sessions, selectedSessionId = selectedSessionId, currentSessionId = currentSessionId)
        return sessions
    }

    suspend fun persistHistory(
        sessions: List<ChatSession>,
        selectedSessionId: Long?,
        currentSessionId: Long?
    ) {
        val snapshot = ChatHistorySnapshot(
            sessions = sessions,
            selectedSessionId = selectedSessionId,
            currentSessionId = currentSessionId
        )
        chatHistoryRepository.saveHistory(snapshot)
    }

    suspend fun loadHistory(): ChatHistorySnapshot {
        return chatHistoryRepository.loadHistory()
    }
}
