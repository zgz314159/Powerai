package com.example.powerai.domain.model

data class KnowledgeEntry(
    val id: String,
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val source: String = "",
    val status: String = "parsed"
)

data class KnowledgeFile(
    val fileId: String,
    val fileName: String,
    val importTimestamp: Long,
    val entries: List<KnowledgeEntry>
)
