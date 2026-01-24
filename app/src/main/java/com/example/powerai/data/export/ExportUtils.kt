package com.example.powerai.data.export

import com.example.powerai.data.local.entity.KnowledgeEntity
import com.google.gson.Gson

object ExportUtils {
    fun toCsv(items: List<KnowledgeEntity>): String {
        val sb = StringBuilder()
        sb.append("id,title,content,source,category,keywords\n")
        items.forEach { e ->
            val line = listOf(e.id.toString(), escape(e.title), escape(e.content), escape(e.source), escape(e.category), escape(e.keywordsSerialized)).joinToString(",")
            sb.append(line).append("\n")
        }
        return sb.toString()
    }

    fun toJson(items: List<KnowledgeEntity>): String = Gson().toJson(items)

    private fun escape(s: String): String {
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}
