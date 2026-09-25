package com.example.powerai.engine.ai

import android.util.Log

/**
 * Pure JNI interface for powerai_llama_jni.
 */
object LlamaNativeBridge {
    private const val TAG = "LlamaNativeBridge"

    init {
        try {
            System.loadLibrary("powerai_llama_jni")
        } catch (t: Throwable) {
            Log.w(TAG, "couldn't load powerai_llama_jni: ${t.message}")
        }
    }

    @JvmStatic
    external fun initBackend(backend: String?): Boolean

    @JvmStatic
    external fun loadModel(path: String?): Boolean

    @JvmStatic
    external fun unloadModel(): Boolean

    @JvmStatic
    external fun generate(prompt: String?, maxTokens: Int): String?

    @JvmStatic
    external fun setOption(key: String?, value: String?): Boolean

    @JvmStatic
    external fun nativeStopGeneration(): Unit

    @JvmStatic
    external fun generateAsync(prompt: String?, maxTokens: Int): Boolean

    @JvmStatic
    external fun nativeGenerateStream(prompt: String?): Boolean
}
