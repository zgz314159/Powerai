package com.example.powerai.domain.repository

/**
 * Minimal vector repository contract used by domain logic.
 */
interface VectorRepository {
    /** Initialize repository/index with vector dimension. */
    fun init(dim: Int = 128)

    /** Upsert ids and vectors (row-major). */
    fun upsert(ids: LongArray, vectors: FloatArray): Boolean

    /** Search top-k nearest ids. */
    fun search(query: FloatArray, k: Int): LongArray

    /** Persist current index to the given path. */
    fun saveIndex(path: String): Boolean

    /** Load index from given path. */
    fun loadIndex(path: String): Boolean
}
