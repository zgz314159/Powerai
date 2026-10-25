package com.example.powerai.data.retriever

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.powerai.core.repository.AnnRetriever
import com.example.powerai.core.model.RetrievalResult
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
    @ApplicationContext private val context: Context,
    private val encoder: com.example.powerai.domain.retrieval.QueryEncoder =
        com.example.powerai.domain.retrieval.ZeroQueryEncoder,
    private val faissClient: FaissClient = DefaultFaissClient
) : AnnRetriever {

    companion object {
        // must match encoder/embedding dimension used elsewhere
        private const val DIMENSION = 384
    }

    override suspend fun search(query: String, k: Int): List<RetrievalResult> =
        withContext(Dispatchers.IO) {
            val indexFile = context.filesDir.resolve("faiss/index.faiss")
            val handle = try {
                faissClient.openIndex(indexFile.absolutePath)
            } catch (t: Throwable) {
                // JNI not available or linking failed in test/device treat as unavailable
                return@withContext emptyList()
            }

            if (handle <= 0) return@withContext emptyList()

            val qvec = try {
                encoder.encode(query).let { arr ->
                    if (arr.size != DIMENSION) FloatArray(DIMENSION) { 0f }
                    else arr
                }
            } catch (t: Throwable) {
                FloatArray(DIMENSION) { 0f }
            }

            val ids = try {
                faissClient.search(handle, qvec, k)
            } catch (t: Throwable) {
                IntArray(0)
            } finally {
                try {
                    faissClient.closeIndex(handle)
                } catch (_: Throwable) {
                }
            }

            return@withContext ids.map { idInt ->
                RetrievalResult(
                    id = idInt.toLong(),
                    score = 0f,
                    confidence = null,
                    source = "local_faiss",
                    metadata = emptyMap(),
                    vectorPresent = false,
                    item = null,
                    debug = null
                )
            }
        }
}
