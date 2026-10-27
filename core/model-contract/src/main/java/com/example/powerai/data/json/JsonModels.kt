package com.example.powerai.data.json

/**
 * Shared JSON knowledge-file models (Phase C7).
 * Kept in model-contract so :feature:jsonrepo and :app JsonRepository share types.
 */
data class JsonEntry(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "",
    val source: String = "",
    val status: String = "parsed",
)

data class JsonKnowledgeFile(
    val fileId: String,
    val fileName: String,
    val importTimestamp: Long,
    val entriesCount: Int,
)
