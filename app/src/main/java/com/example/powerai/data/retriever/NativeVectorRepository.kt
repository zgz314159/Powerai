package com.example.powerai.data.retriever

import android.content.Context
import android.util.Log
import com.example.powerai.domain.repository.VectorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Native-backed VectorRepository implementation.
 * Responsible for lifecycle of native index and basic persist/load to disk.
 */
@Singleton
class NativeVectorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("vector_dim") private val dim: Int = 128,
    @Named("vector_index_path") private val indexPath: String
) : VectorRepository {
    private val TAG = "NativeVectorRepo"
    private val searcher = NativeAnnSearcher.getInstance(dim)
    private val indexFile: File = File(context.filesDir, indexPath)

    override fun init(dim: Int) {
        // Instance already initialized with dimension during creation
        Log.i(TAG, "init dim=$dim")
    }

    override fun upsert(ids: LongArray, vectors: FloatArray): Boolean {
        val ok = searcher.nativeAddVectors(ids, vectors, dim)
        if (ok) {
            // schedule background save (best-effort)
            Thread {
                try {
                    val saved = saveIndex(indexFile.absolutePath)
                    Log.i(TAG, "background save completed: $saved path=${indexFile.absolutePath}")
                } catch (e: Throwable) {
                    Log.e(TAG, "background save failed", e)
                }
            }.start()
        }
        return ok
    }

    override fun search(query: FloatArray, k: Int): LongArray {
        try {
            // Log first 3 dimensions to verify embedding is not all zeros
            val previewN = Math.min(3, query.size)
            val previewList = (0 until previewN).map { i -> query[i] }
            val preview = previewList.joinToString(",") { v -> String.format("%.6f", v) }
            // regular debug log (no noisy forced prints in production)
            Log.d(TAG, "search called dim=${query.size} preview=$preview k=$k")
            // also emit a debug-level preview for tracing if needed
            Log.d(TAG, "VECTOR_PREVIEW: $preview")
        } catch (e: Throwable) {
            Log.e(TAG, "failed to log query preview", e)
        }
        return searcher.nativeSearch(query, k)
    }

    override fun saveIndex(path: String): Boolean {
        return try {
            searcher.saveIndex(path)
        } catch (e: Throwable) {
            Log.e(TAG, "saveIndex failed", e)
            false
        }
    }

    override fun loadIndex(path: String): Boolean {
        return try {
            val ok = searcher.loadIndex(path)
            Log.i(TAG, "loadIndex($path) -> $ok")
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "loadIndex failed", e)
            false
        }
    }

    /** Helper to attempt loading the default index file if present. */
    fun loadDefaultIndexIfExists(): Boolean {
        val f = indexFile
        if (f.exists()) {
            return loadIndex(f.absolutePath)
        }
        return false
    }
}
