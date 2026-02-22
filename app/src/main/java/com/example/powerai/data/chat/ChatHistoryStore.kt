package com.example.powerai.data.chat

import android.util.Log
import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryStore @Inject constructor(
    private val filesDir: File,
    private val gson: Gson
) {
    private val tag = "ChatHistoryStore"
    private val file: File by lazy { File(filesDir, "ai_chat_history.json") }

    suspend fun load(): ChatHistorySnapshot? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) return@runCatching null
            gson.fromJson(json, ChatHistorySnapshot::class.java)
        }.onFailure { t ->
            Log.w(tag, "load failed: ${t.message}", t)
        }.getOrNull()
    }

    suspend fun save(snapshot: ChatHistorySnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val json = gson.toJson(snapshot)
            file.writeText(json, Charsets.UTF_8)
        }.onFailure { t ->
            Log.w(tag, "save failed: ${t.message}", t)
        }
    }
}
