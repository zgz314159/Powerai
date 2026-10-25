package com.example.powerai.domain.retrieval

/**
 * Abstraction for converting a free-form text query into a vector suitable for
 * similarity search (e.g. FAISS). Production implementations will call the
 * appropriate embedding service or model; the prototype supplies a zero vector.
 */
interface QueryEncoder {
    /**
     * Encode a query string as a float vector. The returned array should have the
     * dimensionality expected by the downstream index (see [LocalFaissAnnRetriever]).
     */
    suspend fun encode(query: String): FloatArray
}

/**
 * Simple encoder used in the prototype before a real embedding model is hooked up.
 * Always returns a zero-filled vector of the configured dimension.
 */
object ZeroQueryEncoder : QueryEncoder {
    private const val DEFAULT_DIM = 384

    override suspend fun encode(query: String): FloatArray = FloatArray(DEFAULT_DIM) { 0f }
}
