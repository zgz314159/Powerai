package com.example.powerai.data.repository

import android.content.Context
import com.example.powerai.engine.ai.GemmaModelLoaderType
import com.example.powerai.engine.ai.GemmaReflectionHelper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGemmaModelLoader @Inject constructor() : GemmaModelLoaderType {
    private companion object {
        const val MODEL_PATH = "/data/user/0/com.example.powerai/files/gemma3.task"
    }

    override fun tryLoad(context: Context?): Any? {
        if (context == null) return null
        try {
            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists() || modelFile.length() == 0L) return null

            val builder = LlmInferenceOptions.builder()
            builder.setModelPath(MODEL_PATH)
            builder.setMaxTokens(512)

            GemmaReflectionHelper.applySampling(builder)

            val options = builder.build()
            return LlmInference.createFromOptions(context, options)
        } catch (_: Throwable) {
            return null
        }
    }

    override fun closeEngine(engine: Any?, context: Context?) {
        try {
            if (engine == null) return

            if (context != null) {
                runCatching { File(context.filesDir, "last_res.txt").appendText("\n--- STOPPED_BY_SYSTEM ---\n") }
            }

            if (engine is LlmInference) {
                engine.close()
            } else {
                val m = engine.javaClass.getMethod("close")
                m.invoke(engine)
            }
        } catch (_: Throwable) {}
    }
}
