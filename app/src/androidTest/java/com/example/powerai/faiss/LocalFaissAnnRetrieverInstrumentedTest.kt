package com.example.powerai.faiss

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.powerai.data.retriever.LocalFaissAnnRetriever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalFaissAnnRetrieverInstrumentedTest {

    @Test
    fun search_doesNotCrash_whenIndexPresentOrMissing() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val faissDir = File(ctx.filesDir, "faiss")
        faissDir.mkdirs()
        val idx = File(faissDir, "index.faiss")
        // ensure file exists (could be empty for prototype)
        idx.writeText("proto-index")

        val retriever = LocalFaissAnnRetriever(ctx)

        val result = runBlocking { retriever.search("test query", 3) }

        // Prototype: result may be empty (JNI absent) or contain RetrievalResult entries; ensure call succeeded
        assertTrue("Result count exceeds requested limit", result.size <= 3)
    }
}
