package com.example.powerai.data.remote

import android.util.Log
import com.example.powerai.BuildConfig
import com.example.powerai.data.remote.api.VectorSearchApiService
import com.example.powerai.data.remote.dto.VectorSearchHit
import com.example.powerai.data.remote.dto.VectorSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VectorSearchClient @Inject constructor(
    private val api: VectorSearchApiService
) {
    suspend fun search(query: String, limit: Int = 5): List<VectorSearchHit> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = api.search(
                    VectorSearchRequest(
                        queryText = query,
                        limit = limit,
                        collection = BuildConfig.VECTOR_SEARCH_COLLECTION
                    )
                )
                resp.results
            } catch (t: Throwable) {
                Log.w("VectorSearchClient", "Vector search failed, fallback to DB. reason=${t.message}")
                emptyList()
            }
        }
    }
}
