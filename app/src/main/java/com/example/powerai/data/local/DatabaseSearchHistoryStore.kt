package com.example.powerai.data.local

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val file: File by lazy { File(context.filesDir, "database_search_history.json") }

    suspend fun load(): List<LocalSearchEntry> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext emptyList()
            val text = file.readText()
            val arr = gson.fromJson(text, Array<LocalSearchEntry>::class.java) ?: return@withContext emptyList<LocalSearchEntry>()
            return@withContext arr.toList()
        } catch (_: Throwable) {
            return@withContext emptyList()
        }
    }

    suspend fun save(list: List<LocalSearchEntry>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(list)
            file.writeText(json)
        } catch (_: Throwable) {
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { save(emptyList()) }
}
