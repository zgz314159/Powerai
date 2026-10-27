package com.example.powerai.data.retriever

import com.example.powerai.faiss.FaissNative

/**
 * Thin abstraction over the JNI bridge to allow easier unit testing and
 * potential substitution of alternate backends.
 */
interface FaissClient {
    fun openIndex(path: String): Int
    fun search(handle: Int, query: FloatArray, k: Int): IntArray
    fun closeIndex(handle: Int)
}

/**
 * Production implementation that delegates to [FaissNative].
 */
object DefaultFaissClient : FaissClient {
    override fun openIndex(path: String): Int = FaissNative.openIndex(path)
    override fun search(handle: Int, query: FloatArray, k: Int): IntArray =
        FaissNative.search(handle, query, k)
    override fun closeIndex(handle: Int) = FaissNative.closeIndex(handle)
}
