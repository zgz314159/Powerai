package com.example.powerai.ui.test

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.app.Activity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.powerai.R
import java.io.File
import android.util.Log
import com.example.powerai.data.retriever.NativeAnnSearcher

/**
 * Simple test Activity to create pending embedding JSON files and enqueue the EmbeddingWorker.
 * Launch this Activity on a device/emulator to exercise the embedding pipeline end-to-end.
 */
class EmbeddingTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedding_test)

        val createBtn = findViewById<Button>(R.id.btn_create_pending)
        val runBtn = findViewById<Button>(R.id.btn_run_worker)
        val testJniBtn = findViewById<Button>(R.id.btn_test_jni)
        val syncBtn = findViewById<Button>(R.id.btn_sync_existing)

        // If launched with intent extra `auto_run=true`, trigger the JNI test automatically (debug helper).
        try {
            if (intent?.getBooleanExtra("auto_run", false) == true) {
                Thread {
                    syncExistingEmbeddings()

                    try {
                        val dim = 384
                        val repo = com.example.powerai.data.retriever.NativeVectorRepository(this, dim, "vector_index.bin")

                        val dummyEmbeddingRepo = object : com.example.powerai.domain.repository.EmbeddingRepository {
                            override suspend fun enqueueForEmbedding(items: List<com.example.powerai.domain.model.KnowledgeItem>) {}
                            override suspend fun storeEmbedding(itemId: Long, embedding: FloatArray) {}
                        }

                        val retriever = com.example.powerai.data.retriever.NativeAnnRetriever(this, repo, dummyEmbeddingRepo, dim)

                        // Construct a minimal RetrievalFusionService instance to exercise the full
                        // mapping path (ANN ids -> Room entities) and produce observability logs.
                        val db = androidx.room.Room.databaseBuilder(this, com.example.powerai.data.local.database.AppDatabase::class.java, "powerai.db").allowMainThreadQueries().build()
                        val dao = db.knowledgeDao()

                        val dummyRepo = object : com.example.powerai.domain.repository.KnowledgeRepository {
                            override suspend fun searchLocal(query: String): List<com.example.powerai.domain.model.KnowledgeItem> { return emptyList() }
                            override suspend fun importDocuments(uris: List<String>): Result<Unit> { return Result.success(Unit) }
                            override suspend fun insertBatch(items: List<com.example.powerai.domain.model.KnowledgeItem>) { /* no-op */ }
                            override suspend fun isFileImported(fileId: String): Boolean { return false }
                            override suspend fun markFileImported(fileId: String, fileName: String, timestamp: Long, status: String) { /* no-op */ }
                        }

                        val obs = com.example.powerai.util.ObservabilityService(this)
                        val fusion = com.example.powerai.domain.retrieval.RetrievalFusionService(dummyRepo, obs, retriever, dao)

                        val results = kotlinx.coroutines.runBlocking { fusion.retrieve("变压器", 3, true) }
                        Log.i("AUTO_RUN", "Fusion results size=${results.size}")
                    } catch (t: Throwable) {
                        Log.e("AUTO_RUN", "auto run search failed", t)
                    }
                }.start()
            }
        } catch (_: Throwable) {}

        createBtn.setOnClickListener {
            val base = File(filesDir, "embeddings")
            val pending = File(base, "pending")
            pending.mkdirs()

            val samples = listOf(
                mapOf("id" to "1001", "content" to "变压器 发热 异常 需 停运"),
                mapOf("id" to "1002", "content" to "变压器 短路 故障 原因 分析")
            )
            for (s in samples) {
                val f = File(pending, "${s["id"]}.json")
                if (!f.exists()) {
                    f.writeText("{\"id\":\"${s["id"]}\",\"content\":\"${s["content"]}\"}")
                }
            }
            Toast.makeText(this, "Created pending files: ${pending.absolutePath}", Toast.LENGTH_LONG).show()
        }

        runBtn.setOnClickListener {
            val work = OneTimeWorkRequestBuilder<com.example.powerai.data.worker.EmbeddingWorker>().build()
            WorkManager.getInstance(this)
                .enqueueUniqueWork("powerai_embedding_worker", ExistingWorkPolicy.REPLACE, work)
            Toast.makeText(this, "Enqueued EmbeddingWorker", Toast.LENGTH_SHORT).show()
        }

        testJniBtn.setOnClickListener {
            Thread {
                    try {
                    val dim = 384
                    val repo = com.example.powerai.data.retriever.NativeVectorRepository(this, dim, "vector_index.bin")

                    // prepare 3 test vectors: ids 3001,3002,3003 (simple distinct vectors on first dim)
                    val ids = longArrayOf(3001L, 3002L, 3003L)
                    val vectors = FloatArray(dim * ids.size)
                    for (i in ids.indices) {
                        for (d in 0 until dim) {
                            vectors[i * dim + d] = if (d == 0) i.toFloat() else 0f
                        }
                    }
                    val added = repo.upsert(ids, vectors)
                    Log.i("JNI_TEST", "NativeVectorRepository.upsert returned: $added")

                    // instantiate retriever manually using a no-op EmbeddingRepository (we use HTTP/CLI from prefs)
                    val dummyEmbeddingRepo = object : com.example.powerai.domain.repository.EmbeddingRepository {
                        override suspend fun enqueueForEmbedding(items: List<com.example.powerai.domain.model.KnowledgeItem>) {}
                        override suspend fun storeEmbedding(itemId: Long, embedding: FloatArray) {}
                    }

                    val retriever = com.example.powerai.data.retriever.NativeAnnRetriever(this, repo, dummyEmbeddingRepo, dim)

                    // perform a semantic search for a query likely close to id 3002 (index 1)
                    val queryText = "变压器 短路 故障 原因 分析"
                    val hits = kotlinx.coroutines.runBlocking { retriever.search(queryText, 3) }

                    val sb = StringBuilder()
                    for (h in hits) sb.append(h).append(',')
                    val out = if (sb.isNotEmpty()) sb.toString().trimEnd(',') else ""

                    runOnUiThread {
                        Toast.makeText(this, "Semantic search hits: $out", Toast.LENGTH_LONG).show()
                        Log.i("JNI_TEST", "Semantic search hits: $out")
                    }

                        // Additional direct-check: if we have persisted embeddings like 1001.emb, use it as a query
                        try {
                            val checkFile = File(filesDir, "embeddings/1001.emb")
                            if (checkFile.exists()) {
                                val b = checkFile.readBytes()
                                val fb = java.nio.ByteBuffer.wrap(b).asFloatBuffer()
                                val q = FloatArray(fb.limit())
                                fb.get(q)
                                val hits2 = repo.search(q, 5)
                                val sb2 = StringBuilder()
                                for (h in hits2) sb2.append(h).append(',')
                                val out2 = if (sb2.isNotEmpty()) sb2.toString().trimEnd(',') else ""
                                runOnUiThread {
                                    Toast.makeText(this, "Direct query hits: $out2", Toast.LENGTH_LONG).show()
                                    Log.i("JNI_TEST", "Direct query hits: $out2")
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("JNI_TEST", "direct query failed", t)
                        }
                } catch (e: Throwable) {
                    runOnUiThread {
                        Toast.makeText(this, "JNI test failed: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("JNI_TEST", "error", e)
                    }
                }
            }.start()
        }

        syncBtn.setOnClickListener {
            syncExistingEmbeddings()
        }

        // NOTE: automated benchmark invocation removed — keep manual helper for debugging.
        // To run the benchmark manually in a debug session, call `runNeonBenchmark()` from a debug-only path.
    }

    private fun syncExistingEmbeddings() {
        Thread {
            try {
                val base = File(filesDir, "embeddings")
                if (!base.exists()) {
                    runOnUiThread {
                        Toast.makeText(this, "No embeddings directory found", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val embFiles = base.listFiles { f -> f.extension == "emb" }?.sortedBy { it.name } ?: emptyList()
                if (embFiles.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this, "No .emb files found to sync", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val dim = 384
                val repo = com.example.powerai.data.retriever.NativeVectorRepository(this, dim, "vector_index.bin")

                val batchSize = 20
                val batchIds = mutableListOf<Long>()
                val batchVecs = mutableListOf<FloatArray>()
                var total = 0
                val expectedBytes = (dim * 4).toLong()

                fun flush() {
                    if (batchIds.isEmpty()) return
                    val idsArr = LongArray(batchIds.size)
                    for (i in batchIds.indices) idsArr[i] = batchIds[i]
                    val merged = FloatArray(batchVecs.size * dim)
                    for (i in batchVecs.indices) {
                        System.arraycopy(batchVecs[i], 0, merged, i * dim, dim)
                    }
                    val ok = try {
                        repo.upsert(idsArr, merged)
                    } catch (e: Throwable) {
                        Log.w("SYNC", "upsert batch failed", e)
                        false
                    }
                    Log.i("SYNC", "flushed batch size=${idsArr.size} ok=$ok")
                    total += idsArr.size
                    batchIds.clear()
                    batchVecs.clear()
                }

                for (f in embFiles) {
                    val id = f.nameWithoutExtension.toLongOrNull() ?: continue
                    try {
                        val fileSize = f.length()
                        Log.d("SYNC", "Processing ${f.name}: size=$fileSize, expected=$expectedBytes")
                        if (fileSize != expectedBytes) {
                            Log.w("SYNC", "skipping ${f.name}, size=$fileSize expected=$expectedBytes")
                            continue
                        }

                        val bytes = f.readBytes()
                        val fb = java.nio.ByteBuffer.wrap(bytes).asFloatBuffer()
                        val arr = FloatArray(fb.limit())
                        fb.get(arr)

                        batchIds.add(id)
                        batchVecs.add(arr)
                    } catch (e: Throwable) {
                        Log.w("SYNC", "failed reading ${f.name}", e)
                    }

                    if (batchIds.size >= batchSize) flush()
                }

                flush()

                runOnUiThread {
                    Toast.makeText(this, "Synced $total embeddings to native index", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("SYNC", "error during sync", e)
            }
        }.start()
    }

    // Debug helper: run NEON benchmark (kept as helper; not executed automatically)
    // TODO: move benchmark and indexing helpers into `VectorRepository` for production use.
    private fun runNeonBenchmark() {
        Thread {
            try {
                val dim = 128
                val searcher = com.example.powerai.data.retriever.NativeAnnSearcher.getInstance(dim)
                // Sizes were used during development. Keep here as reference for manual runs.
                val sizes = listOf(100, 500, 1000, 5000)
                val results = mutableListOf<Triple<Int, Double, Double>>() // (n, ms, throughput)

                for (n in sizes) {
                    // prepare ids and vectors
                    val ids = LongArray(n)
                    val vectors = FloatArray(dim * n)
                    for (i in 0 until n) {
                        ids[i] = 10000L + i
                        for (d in 0 until dim) {
                            // simple deterministic values to avoid randomness overhead
                            vectors[i * dim + d] = (i % 7).toFloat() * 0.001f + (d % 5) * 0.0001f
                        }
                    }
                    val added = searcher.nativeAddVectors(ids, vectors, dim)
                    Log.i("JNI_NEON_BENCH", "added=$added n=$n")

                    // warmup
                    val q = FloatArray(dim)
                    q[0] = 1.0f
                    searcher.nativeSearch(q, 10)

                    // measure: run single-query search once and measure native timing from Kotlin side
                    val t0 = System.nanoTime()
                    val hits = searcher.nativeSearch(q, 10)
                    val t1 = System.nanoTime()
                    val elapsedMs = (t1 - t0) / 1_000_000.0
                    val throughput = n / elapsedMs
                    results.add(Triple(n, elapsedMs, throughput))
                    Log.i("JNI_NEON_BENCH", "n=$n elapsed_ms=$elapsedMs throughput=${String.format("%.3f", throughput)}")
                    // small pause between runs
                    Thread.sleep(200)
                }

                // Build markdown table
                val sb = StringBuilder()
                sb.append("# NEON benchmark\n\n")
                sb.append("| vectors | time (ms) | throughput (vec/ms) |\n")
                sb.append("|---:|---:|---:|\n")
                for ((n, ms, tp) in results) {
                    sb.append("| $n | ${"%.3f".format(ms)} | ${"%.3f".format(tp)} |\n")
                }

                // Write to app files so host can pull if desired
                val doc = java.io.File(filesDir, "neon_benchmark.md")
                doc.writeText(sb.toString())
                Log.i("JNI_NEON_BENCH", "wrote local benchmark file: ${doc.absolutePath}")

                // Also log full table for host parsing
                Log.i("JNI_NEON_BENCH", "BENCHMARK_TABLE_START")
                for ((n, ms, tp) in results) {
                    Log.i("JNI_NEON_BENCH", "ROW n=$n ms=${"%.3f".format(ms)} tp=${"%.3f".format(tp)}")
                }
                Log.i("JNI_NEON_BENCH", "BENCHMARK_TABLE_END")

                runOnUiThread {
                    Toast.makeText(this, "NEON benchmark finished, results saved to ${doc.absolutePath}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "Benchmark failed: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("JNI_NEON_BENCH", "error", e)
                }
            }
        }.start()
    }
}
