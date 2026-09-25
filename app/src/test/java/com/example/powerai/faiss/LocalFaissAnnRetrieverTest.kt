package com.example.powerai.faiss

import android.content.Context
import com.example.powerai.data.retriever.FaissClient
import com.example.powerai.data.retriever.LocalFaissAnnRetriever
import com.example.powerai.domain.retrieval.QueryEncoder
import com.example.powerai.domain.retrieval.ZeroQueryEncoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LocalFaissAnnRetrieverTest {

    private val fakeCtx = mock<Context>()
    private val fakeFilesDir = File(System.getProperty("java.io.tmpdir") ?: "")

    private class FakeFaissClient : FaissClient {
        var lastQuery: FloatArray? = null
        var lastK: Int? = null
        var openHandle = 1
        override fun openIndex(path: String): Int = openHandle
        override fun search(handle: Int, query: FloatArray, k: Int): IntArray {
            lastQuery = query.copyOf()
            lastK = k
            return intArrayOf(123, 456)
        }
        override fun closeIndex(handle: Int) {}
    }

    private class CountingEncoder(private val dim: Int) : QueryEncoder {
        override suspend fun encode(query: String): FloatArray {
            val arr = FloatArray(dim) { index -> index.toFloat() }
            return arr
        }
    }

    @Test
    fun search_forwardsEncodedVector_andReturnsResults() = runBlocking {
        whenever(fakeCtx.filesDir).thenReturn(fakeFilesDir)
        val client = FakeFaissClient()
        val encoder = CountingEncoder(LocalFaissAnnRetrieverTest.DIMENSION)
        val retriever = LocalFaissAnnRetriever(fakeCtx, encoder, client)

        val results = retriever.search("xyz", 5)

        // verify the fake client got the encoded vector and the requested k
        assertNotNull(client.lastQuery)
        assertEquals(LocalFaissAnnRetrieverTest.DIMENSION, client.lastQuery!!.size)
        assertEquals(5, client.lastK)
        // check the mapping from ids to RetrievalResult
        assertEquals(2, results.size)
        assertEquals(123L, results[0].id)
        assertEquals(456L, results[1].id)
    }

    @Test
    fun search_emptyOnBadHandle() = runBlocking {
        whenever(fakeCtx.filesDir).thenReturn(fakeFilesDir)
        val client = FakeFaissClient().apply { openHandle = 0 }
        val retriever = LocalFaissAnnRetriever(fakeCtx, ZeroQueryEncoder, client)

        val results = retriever.search("abc", 2)
        assertTrue(results.isEmpty())
    }

    companion object {
        private const val DIMENSION = 384
    }
}
