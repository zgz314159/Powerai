package com.example.powerai.core.repository

/**
 * Minimal vector repository contract.
 */
interface VectorRepository {
    fun init(dim: Int = 128)
    fun upsert(ids: LongArray, vectors: FloatArray): Boolean
    fun search(query: FloatArray, k: Int): LongArray
    fun saveIndex(path: String): Boolean
    fun loadIndex(path: String): Boolean
}
