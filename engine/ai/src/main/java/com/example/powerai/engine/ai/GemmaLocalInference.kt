package com.example.powerai.engine.ai

import android.content.Context
// 该服务主要负责模型加生命周期管理以及本地推理不可用时的回退
// 绝大部分推理和结果清理逻辑已迁移至 GemmaInferenceEngine
// GemmaResponseSanitizer，当前文件保持轻量

import com.example.powerai.domain.generator.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Direct MediaPipe import for deterministic runtime binding (avoid reflection)
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions

/**
 * GemmaLocalInference - MediaPipe Tasks GenAI adapter (best-effort via reflection).
 *
 * Notes:
 * - This implementation attempts to load MediaPipe GenAI runtime reflectively so
 *   the code is tolerant to differing API surface across versions. With the
 *   dependency added in Gradle, the reflective paths should succeed on-device.
 * - Core inference logic lives in [GemmaInferenceEngine]; this class now only
 *   handles model lifecycle (load/close) and delegates to a fallback provider
 *   when the engine is unavailable.
 */
open class GemmaLocalInference(
    private val context: Context?,
    private val loader: GemmaModelLoaderType = GemmaModelLoader
) : LlmInferenceService {

    companion object {
        private const val TAG = "GemmaLocalInference"
    }

    // the repetition detector, cleaner, and other helpers have been moved
    // into GemmaResponseSanitizer in order to shorten this class.

    // Backing handle for the loaded engine - hold as Any and use reflection where
    // necessary to avoid compile-time coupling to exact API shapes.
    @Volatile
    private var engine: Any? = null
    @Volatile
    private var loaded: Boolean = false
    // simple token-collapse detector streak counter

    /**
     * Load the on-device model file. Returns true when engine is available.
     * Model path is fixed per spec: /data/data/com.example.powerai/files/gemma-2b-it-cpu-int4.bin
     */
    fun loadModel(): Boolean {
        // simple diagnostic marker
        try { context?.filesDir?.resolve("entry_load.txt")?.writeText("LOAD_START\n", Charsets.UTF_8) } catch (_: Throwable) {}
        if (loaded) return true
        val created = loader.tryLoad(context)
        if (created != null) {
            engine = created
            loaded = true
            return true
        }
        return false
    }


    override suspend fun infer(prompt: PromptBuilder.Prompt, maxTokens: Int): InferenceResult {
        // Diagnostic marker at entry
        try {
            context?.filesDir?.resolve("entry_infer.txt")?.writeText("INFER_START\n", Charsets.UTF_8)
        } catch (_: Throwable) { }

        // ensure engine loaded lazily
        if (!loaded) {
            try { loadModel() } catch (_: Throwable) { /* ignore */ }
        }

        if (!loaded || engine == null) {
            return fallbackProvider.infer(prompt)
        }

        // delegate to the extracted inference engine
        return try {
            GemmaInferenceEngine.infer(context, engine, prompt, maxTokens)
        } catch (t: Throwable) {
            InferenceResult(text = "[GemmaLocalInference] Local inference failed: ${t.message}", tokensUsed = 0, raw = t.toString())
        }
    }

    // the previous fallbackInference logic has been moved to a dedicated helper to
    // keep this class focused on model lifecycle.  tests now exercise the new
    // GemmaFallbackProvider directly.  a provider instance is cached here to
    // avoid reallocation on every call.
    private val fallbackProvider = GemmaFallbackProvider(context)

    fun close() {
        // delegate to the loader helper which handles diagnostic flushing and
        // reflective close; keep our own state updated afterwards.
        loader.closeEngine(engine, context)
        engine = null
        loaded = false
    }

}

