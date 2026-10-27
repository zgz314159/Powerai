package com.example.powerai.engine.nativecore

import android.content.Context
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.AnnRetriever
import com.example.powerai.core.repository.EmbeddingRepository
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
 */
class NativeAnnRetriever @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeRepo: NativeVectorRepository,
    private val embeddingRepository: EmbeddingRepository,
    @Named("vector_dim") private val dim: Int = 384 // Default dim
) : AnnRetriever {

    override suspend fun search(query: String, k: Int): List<RetrievalResult> = withContext(Dispatchers.IO) {
        val startTotal = System.nanoTime()
        var qvec: FloatArray? = null
        val startEmbed = System.nanoTime()

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
                    }
                } catch (t: Throwable) {
                    throw t
                }
            } else {
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

                    val pythonPath = prefs.getString("embedding_python_path", ".venv\\Scripts\\python.exe")
                        ?: ".venv\\Scripts\\python.exe"
                    val scriptPath = prefs.getString("embedding_cli_path", "tools/embedding_prototype/embed_batch_cli.py")
                        ?: "tools/embedding_prototype/embed_batch_cli.py"

                    val cmd = listOf(pythonPath, scriptPath, batchFile.absolutePath)
                    val pb = ProcessBuilder(cmd)
                    val p = pb.start()
                    val stdout = p.inputStream.bufferedReader().use { it.readText() }
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
                    }
                    try { batchFile.delete() } catch (_: Throwable) {}
                } catch (t: Throwable) {
                    throw t
                }
            }
        } catch (t: Throwable) {
            // Error handling
        }

        val endEmbed = System.nanoTime()

        if (qvec == null) {
            val fallback = FloatArray(dim)
            var h = query.hashCode()
            for (i in 0 until dim) {
                h = h * 31 + i
                fallback[i] = ((h and 0xffff).toFloat()) / 65536.0f
            }
            qvec = fallback
        }

        val startSearch = System.nanoTime()
        val ids = try {
            nativeRepo.search(qvec!!, k)
        } catch (t: Throwable) {
            LongArray(0)
        }
        val endSearch = System.nanoTime()
        val endTotal = System.nanoTime()

        val results = ids.map { id ->
            RetrievalResult(
                id = id,
                score = 0f,
                confidence = null,
                source = "native",
                metadata = emptyMap(),
                provenance = emptyList(),
                vectorPresent = false,
                debug = mapOf("note" to "no_scores_available")
            )
        }

        return@withContext results
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
