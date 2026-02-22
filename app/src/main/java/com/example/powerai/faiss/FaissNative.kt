package com.example.powerai.faiss

object FaissNative {
    init {
        kotlin.runCatching {
            System.loadLibrary("faiss_wrapper")
        }
    }

    external fun openIndex(path: String): Int
    external fun closeIndex(handle: Int)
    external fun search(handle: Int, query: FloatArray, k: Int): IntArray
}
