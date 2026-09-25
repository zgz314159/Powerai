package com.example.powerai.engine.nativecore

import android.content.Context
import com.example.powerai.core.repository.VectorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Native-backed VectorRepository implementation moved from app module.
 */
@Singleton
class NativeVectorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("vector_dim") private val dim: Int = 384,
    @Named("vector_index_path") private val indexPath: String = "vector_index.bin"
) : VectorRepository {

    private val indexFile: File = File(context.filesDir, indexPath)

    companion object {
        init {
            System.loadLibrary("native_search")
        }
    }

    override fun init(dim: Int) {
        nativeInit(dim)
    }

    override fun upsert(ids: LongArray, vectors: FloatArray): Boolean {
        return nativeUpsert(ids, vectors)
    }

    override fun search(query: FloatArray, k: Int): LongArray {
        return nativeSearch(query, k)
    }

    override fun saveIndex(path: String): Boolean {
        return nativeSaveIndex(path)
    }

    override fun loadIndex(path: String): Boolean {
        return nativeLoadIndex(path)
    }

    /** Helper to attempt loading the default index file if present. */
    fun loadDefaultIndexIfExists(): Boolean {
        if (indexFile.exists()) {
            return loadIndex(indexFile.absolutePath)
        }
        return false
    }

    private external fun nativeInit(dim: Int)
    private external fun nativeUpsert(ids: LongArray, vectors: FloatArray): Boolean
    private external fun nativeSearch(query: FloatArray, k: Int): LongArray
    private external fun nativeSaveIndex(path: String): Boolean
    private external fun nativeLoadIndex(path: String): Boolean
}
