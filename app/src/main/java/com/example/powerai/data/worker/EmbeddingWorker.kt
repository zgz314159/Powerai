package com.example.powerai.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.powerai.data.repository.EmbeddingRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Worker that reads pending JSON files under filesDir/embeddings/pending, batches them,
 * posts to a configurable local embedding service endpoint, and persists returned embeddings.
 *
 * Expected local service contract (example): POST /embed_batch with JSON body [{"id":"...","content":"..."},...]
 * Response: {"results": {"id1": [0.1,0.2,...], "id2": [...]}}
 */
@HiltWorker
class EmbeddingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val embeddingRepo: EmbeddingRepositoryImpl
) : CoroutineWorker(context, params) {

    private val TAG = "EmbeddingWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val base = File(applicationContext.filesDir, "embeddings")
            val pending = File(base, "pending")
            if (!pending.exists() || !pending.isDirectory) return@withContext Result.success()

            val files = pending.listFiles { f -> f.extension == "json" }?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.isEmpty()) return@withContext Result.success()

            val batch = JSONArray()
            val startTs = System.currentTimeMillis()
            var processed = 0
            var failures = 0
            for (f in files) {
                try {
                    val txt = f.readText()
                    val obj = org.json.JSONObject(txt)
                    val id = obj.optString("id")
                    val content = obj.optString("content")
                    if (content.isNotBlank()) {
                        val j = org.json.JSONObject()
                        j.put("id", id)
                        j.put("content", content)
                        batch.put(j)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "skipping pending file ${f.name}", t)
                }
            }

            if (batch.length() == 0) return@withContext Result.success()

            // Determine mode: 'http' or 'cli' (default cli)
            val prefs = applicationContext.getSharedPreferences("powerai_prefs", Context.MODE_PRIVATE)
            val mode = prefs.getString("embedding_service_mode", "cli") ?: "cli"
            val results = org.json.JSONObject()

            if (mode == "http") {
                val endpoint = prefs.getString("embedding_service_url", "http://127.0.0.1:8000/embed_batch") ?: "http://127.0.0.1:8000/embed_batch"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                }

                conn.outputStream.use { os ->
                    val body = batch.toString()
                    os.write(body.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "embedding service returned HTTP $code")
                    failures += files.size
                    writeMetrics(applicationContext, startTs, processed, failures)
                    return@withContext Result.retry()
                }

                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val respObj = org.json.JSONObject(resp)
                val tmp = respObj.optJSONObject("results") ?: org.json.JSONObject()
                for (k in tmp.keys()) results.put(k, tmp.get(k))
            } else {
                // CLI mode: write batch to temp file and execute configured python CLI
                val baseDir = File(applicationContext.filesDir, "embeddings")
                baseDir.mkdirs()
                val batchFile = File(baseDir, "batch_${System.currentTimeMillis()}.json")
                batchFile.writeText(batch.toString())

                val pythonPath = prefs.getString("embedding_python_path", ".venv\\Scripts\\python.exe") ?: ".venv\\Scripts\\python.exe"
                val scriptPath = prefs.getString("embedding_cli_path", "tools/embedding_prototype/embed_batch_cli.py") ?: "tools/embedding_prototype/embed_batch_cli.py"

                try {
                    val pb = ProcessBuilder(pythonPath, scriptPath, batchFile.absolutePath)
                    pb.redirectErrorStream(true)
                    val p = pb.start()
                    val out = p.inputStream.bufferedReader().use { it.readText() }
                    val exit = p.waitFor()
                    if (exit != 0) {
                        Log.w(TAG, "embed CLI exited $exit; output=$out")
                        failures += files.size
                        writeMetrics(applicationContext, startTs, processed, failures)
                        return@withContext Result.retry()
                    }
                    val respObj = org.json.JSONObject(out)
                    val tmp = respObj.optJSONObject("results") ?: org.json.JSONObject()
                    for (k in tmp.keys()) results.put(k, tmp.get(k))
                } catch (t: Throwable) {
                    Log.w(TAG, "failed to run embed CLI", t)
                    failures += files.size
                    writeMetrics(applicationContext, startTs, processed, failures)
                    return@withContext Result.retry()
                } finally {
                    try { batchFile.delete() } catch (_: Throwable) {}
                }
            }

            // persist embeddings
            for (key in results.keys()) {
                try {
                    val arr = results.getJSONArray(key)
                    val emb = FloatArray(arr.length())
                    for (i in 0 until arr.length()) emb[i] = arr.getDouble(i).toFloat()
                    // normalize or other postprocessing can be done here
                    // Try to parse numeric id -> long, otherwise skip storeEmbedding by id
                    val longId = key.toLongOrNull()
                    if (longId != null) {
                        embeddingRepo.storeEmbedding(longId, emb)
                    } else {
                        // fallback: write to file named by key
                        val baseDir = File(applicationContext.filesDir, "embeddings")
                        val bin = File(baseDir, "${key}.emb")
                        val fos = bin.outputStream()
                        val bb = java.nio.ByteBuffer.allocate(4 * emb.size)
                        bb.asFloatBuffer().put(emb)
                        fos.write(bb.array())
                        fos.close()
                    }
                    processed += 1
                } catch (t: Throwable) {
                    Log.w(TAG, "failed to persist embedding for key $key", t)
                    failures += 1
                }
            }

            // remove processed pending files
            for (f in files) {
                try { f.delete() } catch (_: Throwable) {}
            }

            val duration = System.currentTimeMillis() - startTs
            writeMetrics(applicationContext, startTs, processed, failures)
            Log.i(TAG, "EmbeddingWorker finished: processed=$processed failures=$failures durationMs=$duration")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "worker failed", t)
            // record failure
            try { writeMetrics(applicationContext, System.currentTimeMillis(), 0, 1) } catch (_: Throwable) {}
            Result.retry()
        }
    }

    private fun writeMetrics(context: Context, startTs: Long, processed: Int, failures: Int) {
        try {
            val base = File(context.filesDir, "embeddings")
            base.mkdirs()
            val metrics = File(base, "embedding_worker_metrics.log")
            val entry = org.json.JSONObject().apply {
                put("ts", startTs)
                put("processed", processed)
                put("failures", failures)
                put("pid", android.os.Process.myPid())
            }
            metrics.appendText(entry.toString() + "\n")
        } catch (t: Throwable) {
            Log.w(TAG, "failed to write metrics", t)
        }
    }
}
