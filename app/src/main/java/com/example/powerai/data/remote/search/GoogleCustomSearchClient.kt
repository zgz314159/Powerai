package com.example.powerai.data.remote.search

// android.util.Log removed; diagnostic logs suppressed
import com.example.powerai.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

class GoogleCustomSearchClient @Inject constructor() {
    private val tag = "WebSearch"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun isConfigured(): Boolean {
        val key = BuildConfig.SERPER_API_KEY.trim()
        val keyOk = key.isNotBlank()
        // log removed: isConfigured provider=serper keyOk=$keyOk keyLen=${key.length}
        return keyOk
    }

    suspend fun search(query: String, count: Int = 5): List<WebSearchResult> {
        val key = BuildConfig.SERPER_API_KEY.trim()
        if (key.isBlank()) {
            // log removed: search skipped: missing SERPER_API_KEY (keyLen=${key.length})
            return emptyList()
        }

        val endpoint = "https://google.serper.dev/search"
        // Serper commonly returns 10 results per page; keep it stable and trim locally.
        val numRequested = 10
        val maxOut = count.coerceIn(1, 10)
        val httpUrl = endpoint.toHttpUrlOrNull()?.newBuilder()?.build()
            ?: run {
                // log removed: search failed: bad endpoint url
                return emptyList()
            }

        // log removed: search provider=serper q='${query.take(60)}' num=$maxOut url=$httpUrl

        val payload = JsonObject().apply {
            addProperty("q", query)
            addProperty("num", numRequested)
            addProperty("hl", "zh-cn")
            addProperty("gl", "cn")
        }
        val json = payload.toString()
        // log removed: search payload=${json.take(200)}

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(httpUrl)
            .post(requestBody)
            .header("X-API-KEY", key)
            .header("Accept", "application/json")
            .header("User-Agent", "PowerAi/1.0")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val bodyRaw = resp.body?.string().orEmpty()
                // log removed: search http response code=$code bodyLen=${bodyRaw.length}
                if (!resp.isSuccessful) {
                    val bodyPreview = bodyRaw.take(2000)
                    // log removed: search http error code=$code body='$bodyPreview'
                    return@use emptyList()
                }

                val body = bodyRaw.trim()
                if (body.isBlank()) {
                    // log removed: search empty body
                    return@use emptyList()
                }

                val root = try { JsonParser().parse(body).asJsonObject } catch (_: Throwable) {
                    // log removed: search parse json failed
                    return@use emptyList()
                }

                // Serper: primary results usually in `organic`; sometimes `news` exists.
                val items = root.getAsJsonArray("organic")
                    ?: root.getAsJsonArray("news")
                    ?: run {
                        // log removed: search ok but no organic/news array
                        return@use emptyList()
                    }

                val out = ArrayList<WebSearchResult>(items.size())
                for (el in items) {
                    val obj = try { el.asJsonObject } catch (_: Throwable) { continue }
                    val title = obj.get("title")?.asString?.trim().orEmpty()
                    val url = (obj.get("link")?.asString ?: obj.get("url")?.asString)?.trim().orEmpty()
                    val snippet = (obj.get("snippet")?.asString ?: obj.get("description")?.asString)?.trim().orEmpty()
                    if (title.isBlank() || url.isBlank()) continue
                    out.add(WebSearchResult(title = title, url = url, snippet = snippet))
                }
                val trimmed = if (out.size > maxOut) out.take(maxOut) else out
                // log removed: search parsed results=${out.size} trimmed=${trimmed.size}
                trimmed
            }
        }
    }
}
