package com.example.powerai.data.retriever

import android.content.Context
import android.util.Log
import com.example.powerai.domain.retriever.AnnRetriever
import com.example.powerai.domain.repository.EmbeddingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named

/**
 * Adapter that exposes native-backed vector search as an AnnRetriever.
 * Attempts to synchronously obtain an embedding for the query via the
 * configured embedding service (HTTP or CLI), falling back to a deterministic
 * hash-derived vector when unavailable.
 */
class NativeAnnRetriever @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeRepo: NativeVectorRepository,
    private val embeddingRepository: EmbeddingRepository,
    @Named("vector_dim") private val dim: Int = 128
) : AnnRetriever {

    private val TAG = "NativeAnnRetriever"

    override suspend fun search(query: String, k: Int): List<Int> = withContext(Dispatchers.IO) {
        val startTotal = System.nanoTime()
        var qvec: FloatArray? = null
        val startEmbed = System.nanoTime()
        Log.i(TAG, "embedding step start ts=${System.currentTimeMillis()} mode_checking...")

        try {
            val prefs = context.getSharedPreferences("powerai_prefs", Context.MODE_PRIVATE)
            val mode = prefs.getString("embedding_service_mode", "cli") ?: "cli"

            if (mode == "http") {
                val endpoint = prefs.getString("embedding_service_url", "http://127.0.0.1:8000/embed_batch")
                    ?: "http://127.0.0.1:8000/embed_batch"
                try {
                    val url = URL(endpoint)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connectTimeout = 15000
                        readTimeout = 60000
                        doOutput = true
                    }
                    val body = "[{\"id\":\"q0\",\"content\":\"" + query.replace("\"","\\\"") + "\"}]"
                    conn.outputStream.use { os -> os.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val respObj = JSONObject(resp)
                        val tmp = respObj.optJSONObject("results") ?: JSONObject()
                        val arr = tmp.optJSONArray("q0")
                        if (arr != null) {
                            val emb = FloatArray(arr.length())
                            for (i in 0 until arr.length()) emb[i] = arr.getDouble(i).toFloat()
                            qvec = normalizeOrPad(emb)
                        }
                    } else {
                        Log.w(TAG, "embedding http returned $code")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "http embedding failed", t)
                    throw t
                }
            } else {
                // CLI mode: reuse same CLI used by EmbeddingWorker to compute single query.
                try {
                    val baseDir = File(context.filesDir, "embeddings")
                    baseDir.mkdirs()
                    val batchFile = File(baseDir, "batch_query_${System.currentTimeMillis()}.json")
                    val arr = org.json.JSONArray()
                    val obj = org.json.JSONObject().apply {
                        put("id", "q0")
                        put("content", query)
                    }
                    arr.put(obj)
                    batchFile.writeText(arr.toString())

                    val prefs = context.getSharedPreferences("powerai_prefs", Context.MODE_PRIVATE)
                    val pythonPath = prefs.getString("embedding_python_path", ".venv\\Scripts\\python.exe")
                        ?: ".venv\\Scripts\\python.exe"
                    val scriptPath = prefs.getString("embedding_cli_path", "tools/embedding_prototype/embed_batch_cli.py")
                        ?: "tools/embedding_prototype/embed_batch_cli.py"

                    val cmd = listOf(pythonPath, scriptPath, batchFile.absolutePath)
                    Log.i(TAG, "embed CLI command: ${cmd.joinToString(" ")}")
                    val pb = ProcessBuilder(cmd)
                    // read stdout and stderr separately to help diagnose permission/path issues
                    val p = pb.start()
                    val stdout = p.inputStream.bufferedReader().use { it.readText() }
                    val stderr = p.errorStream.bufferedReader().use { it.readText() }
                    val exit = p.waitFor()
                    if (exit == 0) {
                        val respObj = JSONObject(stdout)
                        val tmp = respObj.optJSONObject("results") ?: JSONObject()
                        val arr2 = tmp.optJSONArray("q0")
                        if (arr2 != null) {
                            val emb = FloatArray(arr2.length())
                            for (i in 0 until arr2.length()) emb[i] = arr2.getDouble(i).toFloat()
                            qvec = normalizeOrPad(emb)
                        }
                    } else {
                        Log.e(TAG, "embed CLI exit=$exit stdout=${stdout.take(1000)} stderr=${stderr.take(2000)}")
                    }
                    try { batchFile.delete() } catch (_: Throwable) {}
                } catch (t: Throwable) {
                    Log.e(TAG, "cli embedding failed", t)
                    throw t
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "embedding step failed (outer)", t)
        }

        val endEmbed = System.nanoTime()

        if (qvec == null) {
            // fallback deterministic hash-derived vector
            val fallback = FloatArray(dim)
            var h = query.hashCode()
            for (i in 0 until dim) {
                h = h * 31 + i
                fallback[i] = ((h and 0xffff).toFloat()) / 65536.0f
            }
            qvec = fallback
        }

        val startSearch = System.nanoTime()
        Log.i(TAG, "embedding step end ts=${System.currentTimeMillis()} qvec_size=${qvec?.size}")
        val ids = try {
            nativeRepo.search(qvec!!, k)
        } catch (t: Throwable) {
            Log.e(TAG, "native search failed", t)
            LongArray(0)
        }
        val endSearch = System.nanoTime()
        val endTotal = System.nanoTime()

        // Log timing breakdown
        val embedMs = (endEmbed - startEmbed) / 1_000_000.0
        val searchMs = (endSearch - startSearch) / 1_000_000.0
        val totalMs = (endTotal - startTotal) / 1_000_000.0
        Log.i(TAG, "timing embed_ms=${String.format("%.3f", embedMs)} search_ms=${String.format("%.3f", searchMs)} total_ms=${String.format("%.3f", totalMs)}")

        return@withContext ids.map { it.toInt() }
    }

    private fun normalizeOrPad(src: FloatArray): FloatArray {
        if (src.size == dim) return src
        val out = FloatArray(dim)
        val n = Math.min(src.size, dim)
        System.arraycopy(src, 0, out, 0, n)
        if (src.size < dim) {
            for (i in src.size until dim) out[i] = 0f
        }
        return out
    }
}
