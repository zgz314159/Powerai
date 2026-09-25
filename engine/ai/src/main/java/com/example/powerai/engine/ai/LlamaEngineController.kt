package com.example.powerai.engine.ai

import android.content.Context
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * High-level controller for the native model engine lifecycle.
 */
object LlamaEngineController {
    private const val TAG = "LlamaEngineController"
    private const val LOAD_CALL_TIMEOUT_MS = 20_000L

    private val aarExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun loadModel(path: String?, provider: NativeResourceProvider, context: Context?): Boolean {
        return try {
            val future = aarExecutor.submit<Boolean> {
                try {
                    // Initialization logic moved from safeLoadModel
                    LlamaNativeBridge.initBackend("opencl")
                    LlamaNativeBridge.initBackend("vulkan")
                    LlamaNativeBridge.loadModel(path)
                } catch (t: Throwable) {
                    Log.e(TAG, "failed to load model", t)
                    false
                }
            }
            future.get(LOAD_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            Log.e(TAG, "model load timed out or failed", t)
            false
        }
    }

    fun unloadModel(): Boolean {
        val res = LlamaNativeBridge.unloadModel()
        LlamaMetricsManager.reset()
        return res
    }
}
