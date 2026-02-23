package com.example.powerai.data.retriever

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.powerai.domain.retriever.AnnRetriever
import com.example.powerai.faiss.FaissNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Local FAISS-based ANN retriever (prototype).
 *
 * Notes:
 * - This is a thin adapter that calls the JNI bridge `FaissNative`.
 * - For the prototype we use a fixed embedding dim (384) and a zero-vector placeholder
 *   for query->embedding. Replace with real embedding encoder when available.
 */
class LocalFaissAnnRetriever @Inject constructor(
    @ApplicationContext private val context: Context
) : AnnRetriever {

    private val dim = 384

    override suspend fun search(query: String, k: Int): List<Long> = withContext(Dispatchers.IO) {
        val indexFile = context.filesDir.resolve("faiss/index.faiss")
        val handle = try {
            FaissNative.openIndex(indexFile.absolutePath)
        } catch (t: Throwable) {
            // JNI not available or linking failed in test/device — treat as unavailable
            return@withContext emptyList()
        }

        if (handle <= 0) return@withContext emptyList()

        // TODO: replace with actual encoder
        val qvec = FloatArray(dim) { 0.0f }
        val ids = try {
            FaissNative.search(handle, qvec, k)
        } catch (t: Throwable) {
            IntArray(0)
        } finally {
            try {
                FaissNative.closeIndex(handle)
            } catch (_: Throwable) {
            }
        }

        return@withContext ids.map { it.toLong() }
    }
}
