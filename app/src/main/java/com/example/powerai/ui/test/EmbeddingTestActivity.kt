package com.example.powerai.ui.test

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.powerai.R
import com.example.powerai.domain.retrieval.HybridRetrievalService
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * Debugging activity to verify vector engine initialization and search functionality.
 */
@AndroidEntryPoint
class EmbeddingTestActivity : ComponentActivity() {

    @Inject
    lateinit var hybridRetrievalService: HybridRetrievalService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedding_test)

        findViewById<Button>(R.id.btn_test_jni).setOnClickListener {
            testJniSearch()
        }

        findViewById<Button>(R.id.btn_test_fts).setOnClickListener {
            testFtsSearch()
        }

        findViewById<Button>(R.id.btn_sync_existing).setOnClickListener {
            syncExistingEmbeddings()
        }

        autoRunIfRequested()
    }

    private fun syncExistingEmbeddings() {
        val baseDir = File(filesDir, "embeddings")
        if (!baseDir.exists()) {
            Toast.makeText(this, "No embeddings directory found", Toast.LENGTH_SHORT).show()
            return
        }

        val embFiles = baseDir.listFiles { _, name -> name.endsWith(".emb") } ?: emptyArray()
        if (embFiles.isEmpty()) {
            Toast.makeText(this, "No .emb files found", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val dim = 384
                val repo = com.example.powerai.engine.nativecore.NativeVectorRepository(this)
                repo.init(dim)

                for (file in embFiles) {
                    val idStr = file.name.removeSuffix(".emb")
                    val id = idStr.toLongOrNull() ?: continue
                    val b = file.readBytes()
                    val fb = java.nio.ByteBuffer.wrap(b).asFloatBuffer()
                    val vec = FloatArray(fb.limit())
                    fb.get(vec)

                    if (vec.size == dim) {
                        repo.upsert(longArrayOf(id), vec)
                    }
                }

                // save index for persistence
                repo.saveIndex(File(filesDir, "vector_index.bin").absolutePath)

                runOnUiThread {
                    Toast.makeText(this, "Synced ${embFiles.size} embeddings to native index", Toast.LENGTH_LONG).show()
                    Log.i("SYNC_TEST", "Synced ${embFiles.size} embeddings")
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "Sync failed: ${t.message}", Toast.LENGTH_LONG).show()
                    Log.e("SYNC_TEST", "error", t)
                }
            }
        }.start()
    }

    private fun testJniSearch() {
        Thread {
            try {
                val dim = 384
                val repo = com.example.powerai.engine.nativecore.NativeVectorRepository(this)
                repo.init(dim)

                // Try to load existing index if possible
                val indexFile = File(filesDir, "vector_index.bin")
                if (indexFile.exists()) {
                    repo.loadIndex(indexFile.absolutePath)
                }

                // instantiate retriever manually using a no-op EmbeddingRepository (we use HTTP/CLI from prefs)
                val dummyEmbeddingRepo = object : com.example.powerai.core.repository.EmbeddingRepository {
                    override suspend fun enqueueForEmbedding(items: List<com.example.powerai.core.model.KnowledgeItem>) {}
                    override suspend fun storeEmbedding(itemId: Long, embedding: FloatArray) {}
                }

                val retriever = com.example.powerai.engine.nativecore.NativeAnnRetriever(this, repo, dummyEmbeddingRepo, dim)

                // perform a semantic search for a query likely close to id 3002 (index 1)
                val queryText = "变压器 短路 故障 原因 分析"
                val hits = kotlinx.coroutines.runBlocking { retriever.search(queryText, 3) }

                val sb = StringBuilder()
                for (h in hits.mapNotNull { it.id }) sb.append(h).append(',')
                val out = if (sb.isNotEmpty()) sb.toString().trimEnd(',') else ""

                runOnUiThread {
                    Toast.makeText(this@EmbeddingTestActivity, "Semantic search hits: $out", Toast.LENGTH_LONG).show()
                    Log.i("JNI_TEST", "Semantic search hits: $out")
                }

                // Additional direct-check: if we have persisted embeddings like 1001.emb, use it as a query
                testDirectEmbeddingQuery(repo, dim)
            } catch (e: Throwable) {
                runOnUiThread {
                    Toast.makeText(this@EmbeddingTestActivity, "JNI test failed: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("JNI_TEST", "error", e)
                }
            }
        }.start()
    }
    
    private fun testDirectEmbeddingQuery(repo: com.example.powerai.engine.nativecore.NativeVectorRepository, dim: Int) {
        try {
            val checkFile = File(filesDir, "embeddings/1001.emb")
            if (checkFile.exists()) {
                val b = checkFile.readBytes()
                val fb = java.nio.ByteBuffer.wrap(b).asFloatBuffer()
                val q = FloatArray(fb.limit())
                fb.get(q)
                val hits = repo.search(q, 5)
                val sb = StringBuilder()
                for (h in hits) sb.append(h).append(',')
                val out = if (sb.isNotEmpty()) sb.toString().trimEnd(',') else ""
                runOnUiThread {
                    Toast.makeText(this, "Direct query hits: $out", Toast.LENGTH_LONG).show()
                    Log.i("JNI_TEST", "Direct query hits: $out")
                }
            }
        } catch (t: Throwable) {
            Log.e("JNI_TEST", "direct query failed", t)
        }
    }
    
    private fun testFtsSearch() {
        Thread {
            try {
                val db = androidx.room.Room.databaseBuilder(this, com.example.powerai.core.data.database.AppDatabase::class.java, "powerai.db").allowMainThreadQueries().build()
                val dao = db.knowledgeDao()
                // Use simple MATCH query for Chinese token recall; add wildcard to increase recall
                val rawQuery = "变压器"
                val hits = kotlinx.coroutines.runBlocking { dao.searchByFts(rawQuery) }
                val ids = hits.joinToString(",") { it.id.toString() }
                runOnUiThread {
                    Toast.makeText(this@EmbeddingTestActivity, "FTS hits: $ids", Toast.LENGTH_LONG).show()
                    Log.i("FTS_TEST", "hits=$ids")
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    Toast.makeText(this@EmbeddingTestActivity, "FTS test failed: ${t.message}", Toast.LENGTH_LONG).show()
                    Log.e("FTS_TEST", "error", t)
                }
            }
        }.start()
    }
    
    private fun autoRunIfRequested() {
        try {
            Thread {
                try {
                    Log.i("AUTO_RUN", "auto_run triggered: syncing embeddings and running hybrid retrieval")
                    syncExistingEmbeddings()
                    performAutoRunRetrieval()
                } catch (t: Throwable) {
                    Log.e("AUTO_RUN", "auto run search failed", t)
                }
            }.start()
        } catch (_: Throwable) {}
    }
    
    private fun performAutoRunRetrieval() {
        try {
            val dim = 384
            val repo = com.example.powerai.engine.nativecore.NativeVectorRepository(this)
            repo.init(dim)

            val dummyEmbeddingRepo = object : com.example.powerai.core.repository.EmbeddingRepository {
                override suspend fun enqueueForEmbedding(items: List<com.example.powerai.core.model.KnowledgeItem>) {}
                override suspend fun storeEmbedding(itemId: Long, embedding: FloatArray) {}
            }

            val retriever = com.example.powerai.engine.nativecore.NativeAnnRetriever(this, repo, dummyEmbeddingRepo, dim)

            // Use injected HybridRetrievalService instead of constructing a manual fusion
            val query = intent?.getStringExtra("search_query") ?: "变压器"
            Log.i("AUTO_RUN", "auto_run using query=$query")
            val results = kotlinx.coroutines.runBlocking { hybridRetrievalService.retrieveHybrid(query, 50) }
            Log.i("AUTO_RUN", "Hybrid results size=${results.size}")

            try {
                val builder = com.example.powerai.domain.generator.PromptBuilder(tokenBudget = 600)
                val prompt = builder.buildPrompt(query, results)
                logPromptDebugInfo(prompt, results)
                performAutoRunInference(prompt)
            } catch (t: Throwable) {
                Log.e("AUTO_RUN", "prompt build or inference failed", t)
            }
        } catch (t: Throwable) {
            Log.e("AUTO_RUN", "performAutoRunRetrieval failed", t)
        }
    }
    
    private fun logPromptDebugInfo(prompt: com.example.powerai.domain.generator.PromptBuilder.Prompt, results: List<com.example.powerai.core.model.RetrievalResult>) {
        try {
            Log.i("PROMPT_RAW", prompt.user.take(500))
        } catch (_: Throwable) {}
        try {
            val sources = results.map { it.metadata["fileId"] ?: it.metadata["source"] ?: "(no-fileId)" }
            Log.i("SOURCE_CHECK", "Found Source: $sources")
        } catch (_: Throwable) {}
    }
    
    private fun performAutoRunInference(prompt: com.example.powerai.domain.generator.PromptBuilder.Prompt) {
        try {
            // Give the native engine a short warmup window per Gemma3 guidance
            try { Thread.sleep(3000) } catch (_: Throwable) {}
            val gemma = com.example.powerai.engine.ai.GemmaLocalInference(this@EmbeddingTestActivity)
            val inferResult = kotlinx.coroutines.runBlocking { gemma.infer(prompt, 512) }
            Log.i("AUTO_RUN_INFER", "Inference result: ${inferResult.text.take(200)}...")
            Log.i("AUTO_RUN_INFER", "Metrics: ${inferResult.tokensUsed}")
            
            // Marker for automated log capture scripts to confirm end-to-end success
            Log.i("AUTO_RUN_FINAL", "SUCCESS: query processing and local inference complete.")
        } catch (t: Throwable) {
            Log.e("AUTO_RUN_INFER", "Inference failed", t)
        }
    }
}
