package com.example.powerai.data.remote.bing

import com.example.powerai.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BingWebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

class BingWebSearchClient @Inject constructor() {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun search(query: String, count: Int = 5): List<BingWebSearchResult> {
        val key = BuildConfig.BING_SEARCH_API_KEY.trim()
        if (key.isBlank()) return emptyList()

        val endpoint = BuildConfig.BING_SEARCH_ENDPOINT.trim().ifBlank {
            "https://api.bing.microsoft.com/v7.0/search"
        }

        val httpUrl = endpoint.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("count", count.coerceIn(1, 10).toString())
            ?.addQueryParameter("mkt", BuildConfig.BING_SEARCH_MKT.trim().ifBlank { "zh-CN" })
            ?.build()
            ?: return emptyList()

        val req = Request.Builder()
            .url(httpUrl)
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string()?.trim().orEmpty()
                if (body.isBlank()) return@use emptyList()

                val root = try {
                    JsonParser().parse(body).asJsonObject
                } catch (_: Throwable) {
                    return@use emptyList()
                }

                val webPages = root.getAsJsonObject("webPages") ?: return@use emptyList()
                val values = webPages.getAsJsonArray("value") ?: return@use emptyList()

                val out = ArrayList<BingWebSearchResult>(values.size())
                for (el in values) {
                    val obj = try {
                        el.asJsonObject
                    } catch (_: Throwable) {
                        continue
                    }
                    val title = obj.get("name")?.asString?.trim().orEmpty()
                    val url = obj.get("url")?.asString?.trim().orEmpty()
                    val snippet = obj.get("snippet")?.asString?.trim().orEmpty()
                    if (title.isBlank() || url.isBlank()) continue
                    out.add(BingWebSearchResult(title = title, url = url, snippet = snippet))
                }
                out
            }
        }
    }
}
