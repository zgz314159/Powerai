package com.example.powerai.engine.ai

import android.content.Context
import com.example.powerai.domain.generator.PromptBuilder
import java.io.File
import java.io.FileOutputStream

/**
 * Helper that encapsulates the deterministic fallback inference used when the
 * on-device Gemma engine is not available.  The logic was previously embedded
 * inside [GemmaLocalInference] and has been extracted to enable targeted
 * testing and easier future enhancement (e.g. plug-in more sophisticated
 * backoff strategies).
 */
class GemmaFallbackProvider(private val context: Context?) {
    companion object {
        const val MODEL_UNAVAILABLE_MARKER = "[GemmaLocalInference] No engine available"
    }

    /**
     * Produce an inference result for the given prompt.  The supplied
     * [searcherFactory] is invoked to obtain a [SparseSearcher]; callers may
     * provide a lambda that returns a mocked instance for unit testing.  By
     * default the factory constructs one using the provider's context.
     */
    fun infer(
        prompt: PromptBuilder.Prompt,
        searcherFactory: () -> SparseSearcher = { SparseSearcher(context) }
    ): InferenceResult {
        val msg = "[GemmaFallbackProvider] No engine available, using deterministic fallback"
        try {
            var intentQuery: String? = null
            try {
                if (context is android.app.Activity) {
                    try {
                        intentQuery = context.intent.getStringExtra("search_query")
                    } catch (_: Throwable) {
                        intentQuery = null
                    }
                }
            } catch (_: Throwable) {
                intentQuery = null
            }

            val iq = intentQuery
            val q = if (!iq.isNullOrBlank()) iq.trim() else "(no-query)"
            val sparseSearcher = try {
                searcherFactory()
            } catch (_: Throwable) {
                SparseSearcher(context)
            }
            val entries = try {
                sparseSearcher.search(q, 2)
            } catch (_: Throwable) {
                emptyList<SparseSearcher.SafetyEntry>()
            }

            val finalText = if (entries.isNotEmpty()) {
                val top = entries[0]
                val title = if (top.title.isNullOrBlank()) "(未命名条目)" else top.title
                "AI 正在思考（本地模型加载中）... 规程建议：${title} （来源：电力安全工作规程）"
            } else {
                val displayQuery = if (q.isBlank() || q == "(no-query)") "相关规程" else q
                "AI 正在思考（本地模型加载中）... 规程建议：${displayQuery} （来源：电力安全工作规程）"
            }

            // attempt to persist fallback trace for diagnostics; skipped when
            // app storage is unavailable (a null filesDir would otherwise turn
            // File(...) into a relative path in the current working directory)
            context?.filesDir?.let { filesDir ->
                try {
                    val f = File(filesDir, "last_res.txt")
                    try {
                        f.writeText("[[FALLBACK_DECODE]]\n")
                        f.appendText("--- FINAL_DECODE ---\n")
                        f.appendText(finalText, Charsets.UTF_8)
                        f.appendText("\n--- END OF SESSION ---\n")
                        try { FileOutputStream(f, true).fd.sync() } catch (_: Throwable) {}
                    } catch (_: Throwable) {
                        // ignore write failures
                    }
                } catch (_: Throwable) {
                    // ignore filesystem issues entirely
                }
            }

            return InferenceResult(
                text = finalText,
                tokensUsed = finalText.split(Regex("\\s+")).filter { it.isNotBlank() }.size,
                raw = null
            )
        } catch (_: Throwable) {
            return InferenceResult(text = msg, tokensUsed = 0, raw = null)
        }
    }
}