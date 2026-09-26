package com.example.powerai.domain.model.chat

data class ChatTurn(
    val id: Long,
    val question: String,
    val answer: String,
    val askedAtMillis: Long,
    val sources: List<String> = emptyList(),
    val isError: Boolean = false
)

data class ChatSession(
    val id: Long,
    val title: String,
    val turns: List<ChatTurn> = emptyList(),
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class ChatHistorySnapshot(
    val version: Int = 1,
    val sessions: List<ChatSession> = emptyList(),
    val selectedSessionId: Long? = null,
    val currentSessionId: Long? = null
)
