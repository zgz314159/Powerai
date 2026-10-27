package com.example.powerai.data.repository

import com.example.powerai.data.chat.ChatHistoryStore
import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.example.powerai.domain.repository.ChatHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepositoryImpl @Inject constructor(
    private val store: ChatHistoryStore
) : ChatHistoryRepository {
    override suspend fun loadHistory(): ChatHistorySnapshot {
        return store.load() ?: ChatHistorySnapshot()
    }

    override suspend fun saveHistory(snapshot: ChatHistorySnapshot) {
        store.save(snapshot)
    }

    override suspend fun clearHistory() {
        store.save(ChatHistorySnapshot())
    }
}
