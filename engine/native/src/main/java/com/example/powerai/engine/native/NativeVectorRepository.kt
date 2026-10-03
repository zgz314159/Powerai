package com.example.powerai.engine.nativecore

import android.content.Context
import com.example.powerai.core.repository.VectorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * Native-backed VectorRepository implementation moved from app module.
 *
 * All access to the single native index is serialized by [lock] so a rebuild-triggered [clear]
 * can never run concurrently with an in-flight [search] or [upsert].
 */
@Singleton
class NativeVectorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("vector_dim") private val dim: Int = 384,
    @Named("vector_index_path") private val indexPath: String = "vector_index.bin"
) : VectorRepository {

    private val indexFile: File = File(context.filesDir, indexPath)
    private val lock = ReentrantLock()

    companion object {
        init {
            System.loadLibrary("native_search")
        }
    }

    init {
        lock.withLock {
            check(nativeInit(dim)) {
                "Failed to initialize native vector index with dimension $dim"
            }
        }
    }

    override fun init(dim: Int) {
        lock.withLock {
            check(nativeInit(dim)) {
                "Failed to initialize native vector index with dimension $dim"
            }
        }
    }

    override fun upsert(
        ids: LongArray,
        vectors: FloatArray,
    ): Boolean = lock.withLock { nativeUpsert(ids, vectors) }

    override fun search(
        query: FloatArray,
        k: Int,
    ): LongArray = lock.withLock { nativeSearch(query, k) }

    override fun saveIndex(path: String): Boolean = lock.withLock { nativeSaveIndex(path) }

    override fun loadIndex(path: String): Boolean = lock.withLock { nativeLoadIndex(path) }

    override fun clear() {
        lock.withLock {
            check(nativeInit(dim)) {
                "Failed to reset native vector index with dimension $dim"
            }
        }
    }

    /** Helper to attempt loading the default index file if present. */
    fun loadDefaultIndexIfExists(): Boolean {
        if (indexFile.exists()) {
            return loadIndex(indexFile.absolutePath)
        }
        return false
    }

    private external fun nativeInit(dim: Int): Boolean
    private external fun nativeUpsert(ids: LongArray, vectors: FloatArray): Boolean
    private external fun nativeSearch(query: FloatArray, k: Int): LongArray
    private external fun nativeSaveIndex(path: String): Boolean
    private external fun nativeLoadIndex(path: String): Boolean
}
