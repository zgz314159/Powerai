package com.example.powerai.core.model

/**
 * Unified retrieval result model used across ANN hits and fused results.
 */
data class RetrievalResult(
    /** Optional original ANN/vector id when result comes from vector index. */
    val id: Long? = null,

    /** Primary score used for ranking (0..1 for fused results, raw for native if not normalized). */
    val score: Float = 0f,

    /** Normalized confidence value (0..1) when available. */
    val confidence: Float? = null,

    /** Source label (e.g. "native", "bm25", "vector_client"). */
    val source: String = "",

    /** Lightweight metadata map (title, docId, chunkIndex, etc). */
    val metadata: Map<String, String> = emptyMap(),

    /** Provenance entries pointing back to original entities/chunks. */
    val provenance: List<Provenance> = emptyList(),

    /** Whether the vector payload was attached/available. */
    val vectorPresent: Boolean = false,

    /** Optional debug information. Keep types conservative (primitive wrappers). */
    val debug: Map<String, Any>? = null,

    /** Optional domain `KnowledgeItem` when the hit has been materialized from DB. */
    val item: KnowledgeItem? = null
)

data class Provenance(
    val entityId: Long,
    val snippet: String? = null,
    val offset: Int? = null,
    val length: Int? = null
)
