package com.example.powerai.engine.ai

import android.content.Context
import com.example.powerai.domain.generator.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracted inference loop from [GemmaLocalInference].
 *
 * This object now encapsulates the heavy IO-bound logic that assembles prompts,
 * invokes the MediaPipe engine via reflection, and performs forensic logging.
 * It is deliberately kept as a stateless helper so the surrounding service can
 * manage lifecycle (loading/closing) separately.
 */
object GemmaInferenceEngine {
    /**
     * Format a short RAG-style prompt from retrieved context entries and a user query.
     *
     * This logic was originally embedded in [GemmaLocalInference]; extracting it here
     * allows both the engine and external callers (including unit tests) to reuse the
     * same formatting rules.  Entries list is expected to already be trimmed (e.g. top
     * one only) by the caller.
     */
    fun formatRAGPrompt(entries: List<SparseSearcher.SafetyEntry>, query: String): String {
        val sb = StringBuilder()
        if (entries.isNotEmpty()) {
            var idx = 1
            for (e in entries) {
                val src = if (e.source.isNullOrBlank()) "unknown" else e.source
                sb.append("参考${idx}: ${e.content} (来源: 《${src}》)\n")
                idx += 1
            }
            sb.append("\n")
        } else {
            sb.append("参考: 暂无相关规程记录\n\n")
        }
        sb.append("问题: $query\n要求: 仅根据规程简要回答，严禁超出范围，不超过 30 字。\n回答:\n")
        return sb.toString()
    }

    suspend fun infer(
        context: Context?,
        engine: Any?,
        prompt: PromptBuilder.Prompt,
        maxTokens: Int
    ): InferenceResult {
        return try {
            withContext(Dispatchers.IO) {
                try {
                    // assemble user prompt and retrieval query exactly as before
                    val userContent = if (prompt.user.isNotBlank()) prompt.user else "请根据提供的规程回答以下问题。"
                    val searchQuery = try {
                        var intentQuery: String? = null
                        try {
                            if (context is android.app.Activity) {
                                try { intentQuery = context.intent.getStringExtra("search_query") } catch (_: Throwable) { intentQuery = null }
                            }
                        } catch (_: Throwable) { intentQuery = null }
                        val iq2 = intentQuery
                        if (!iq2.isNullOrBlank()) {
                            iq2.trim()
                        } else {
                            var q = userContent
                            if (q.contains("问题:")) q = q.substringAfter("问题:")
                            else if (q.contains("问题：")) q = q.substringAfter("问题：")
                            q = q.trim()
                            if (q.isBlank()) userContent else q
                        }
                    } catch (_: Throwable) { userContent }
                    val sparseSearcher = SparseSearcher(context)
                    val contextEntries = try { sparseSearcher.search(searchQuery, 2) } catch (_: Throwable) { emptyList<SparseSearcher.SafetyEntry>() }
                    val topOneEntries = if (contextEntries.isNotEmpty()) listOf(contextEntries[0]) else emptyList()
                    try {
                        val combined = topOneEntries.joinToString("\n") { it.content ?: "" }
                        if (combined.isNotBlank()) {
                            val total = combined.length
                            val nonPrintable = combined.count { ch ->
                                ch.code < 0x20 && ch !in listOf('\n', '\r', '\t')
                            }
                            val ratio = nonPrintable.toDouble() / total
                            if (ratio > 0.3) {
                                return@withContext InferenceResult(
                                    text = "[检索内容编码异常]",
                                    tokensUsed = 0,
                                    raw = null
                                )
                            }
                        }
                    } catch (_: Throwable) {}
                    var finalPrompt = formatRAGPrompt(topOneEntries, userContent)
                    if (finalPrompt.length > 1000) finalPrompt = finalPrompt.take(1000)
                    try {
                        context?.filesDir?.resolve("last_res.txt")?.writeText("DEBUG: Prompt formatted...\n", Charsets.UTF_8)
                    } catch (_: Throwable) {}

                    val genOpts = mutableMapOf<String, Any>().apply {
                        this["maxTokens"] = 512
                        this["topK"] = 40
                        this["temperature"] = 0.7f
                        this["stopSequences"] = listOf<String>()
                        this["randomSeed"] = 42
                        this["enableStreaming"] = true
                        this["multiPrefill"] = true
                    }

                    GemmaResponseLogger.writePromptMarker(context, finalPrompt)

                    val invocation = GemmaReflectionHelper.invokeGenerate(engine!!, finalPrompt, genOpts)
                    var text = invocation.text ?: "[GemmaLocalInference] generate failed: no matching API; engine=${engine!!::class.java.name}\n${engine.toString()}"
                    val finishReasonVal = invocation.finishReason
                    val tokenIdsCaptured = invocation.tokenIds
                    val lastResponseObj = invocation.raw

                    val tokenCountForResult = GemmaResponseLogger.writeResponse(
                        context,
                        contextEntries,
                        text,
                        lastResponseObj,
                        tokenIdsCaptured,
                        finishReasonVal
                    )

                    InferenceResult(text = text, tokensUsed = tokenCountForResult, raw = null)
                } catch (inner: Throwable) {
                    InferenceResult(text = "[GemmaLocalInference] engine invocation error", tokensUsed = 0, raw = inner.toString())
                }
            }
        } catch (t: Throwable) {
            InferenceResult(text = "[GemmaLocalInference] Local inference failed: ${t.message}", tokensUsed = 0, raw = t.toString())
        }
    }
}