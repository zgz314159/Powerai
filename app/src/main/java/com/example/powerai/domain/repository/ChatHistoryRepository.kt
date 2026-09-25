package com.example.powerai.domain.repository

import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.example.powerai.domain.model.chat.ChatSession

interface ChatHistoryRepository {
    suspend fun loadHistory(): ChatHistorySnapshot
    suspend fun saveHistory(snapshot: ChatHistorySnapshot)
    suspend fun clearHistory()
}
