package com.example.powerai.engine.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File

/**
 * Helper responsible solely for loading the MediaPipe Gemma model and creating
 * the corresponding engine instance.  Separated from [GemmaLocalInference]
 * to reduce class size and isolate file/IO logic.
 */
interface GemmaModelLoaderType {
    fun tryLoad(context: Context?): Any?
    fun closeEngine(engine: Any?, context: Context?)
}

object GemmaModelLoader : GemmaModelLoaderType {
    private const val MODEL_PATH = "/data/user/0/com.example.powerai/files/gemma3.task"

    /**
     * Attempt to initialize and return a MediaPipe engine instance. Returns
     * null if the model is not available or initialization fails.
     */
    override fun tryLoad(context: Context?): Any? {
        try {
            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                return null
            }
            // Optionally compute hash or log for diagnostics if needed
        } catch (_: Throwable) {
            // ignore, loader will return null below
        }

        try {
            val builder = LlmInferenceOptions.builder()
            try { builder.setModelPath(MODEL_PATH) } catch (_: Throwable) {}
            try { builder.setMaxTokens(512) } catch (_: Throwable) {}

            // apply sampling/reflection tweaks
            // apply optional sampling tweaks via shared reflection helper
            GemmaReflectionHelper.applySampling(builder)

            val options = try { builder.build() } catch (_: Throwable) { null }
            if (options != null) {
                return try {
                    LlmInference.createFromOptions(context, options)
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (_: Throwable) {
            // fallthrough
        }
        return null
    }

    /**
     * Gracefully close a previously-created engine instance.  This mirrors the
     * behaviour that used to live inside [GemmaLocalInference.close], including
     * writing a small marker file and invoking the `close()` method reflectively
     * if available.  If `engine` is null the call is a no-op.
     */
    override fun closeEngine(engine: Any?, context: Context?) {
        try {
            if (engine == null) return
            // attempt to flush any diagnostic traces first, similar to old impl;
            // skipped when app storage is unavailable (a null filesDir would
            // otherwise turn File(...) into a relative path in the current
            // working directory). The reflective close() below always runs.
            context?.filesDir?.let { filesDir ->
                try {
                    val f = java.io.File(filesDir, "last_res.txt")
                    try {
                        val marker = "\n--- STOPPED_BY_SYSTEM ---\n"
                        java.io.FileOutputStream(f, true).use { fos ->
                            fos.write(marker.toByteArray(Charsets.UTF_8))
                            fos.flush()
                            try { fos.fd.sync() } catch (_: Throwable) { }
                        }
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {}
            }

            try {
                val m = engine.javaClass.getMethod("close")
                m.invoke(engine)
            } catch (_: NoSuchMethodException) {
                // ignore if runtime doesn't expose close
            }
        } catch (_: Throwable) {
            // swallow everything; caller should not crash
        }
    }
}
