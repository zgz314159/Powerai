package com.example.powerai.data.retriever

/**
 * JNI-backed ANN searcher with simple singleton lifecycle helpers.
 * Exposes save/load index primitives to persist index to disk.
 */
class NativeAnnSearcher private constructor(private val dim: Int) {
    init {
        System.loadLibrary("native_search")
        nativeInit(dim)
    }

    external fun nativeInit(dim: Int): Boolean
    external fun nativeAddVectors(ids: LongArray, vectors: FloatArray, dim: Int): Boolean
    external fun nativeSearch(query: FloatArray, k: Int): LongArray
    external fun nativeSaveIndex(path: String): Boolean
    external fun nativeLoadIndex(path: String): Boolean

    fun saveIndex(path: String): Boolean = nativeSaveIndex(path)
    fun loadIndex(path: String): Boolean = nativeLoadIndex(path)

    companion object {
        @Volatile
        private var INSTANCE: NativeAnnSearcher? = null

        fun getInstance(dim: Int = com.example.powerai.AppConfig.VECTOR_DIM): NativeAnnSearcher {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NativeAnnSearcher(dim).also { INSTANCE = it }
            }
        }
    }
}
