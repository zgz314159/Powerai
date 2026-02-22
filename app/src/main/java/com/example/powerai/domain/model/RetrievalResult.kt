package com.example.powerai.domain.model

data class RetrievalResult(
    val item: KnowledgeItem,
    /** score in range 0..1 representing estimated relevance/confidence */
    val score: Float
)
