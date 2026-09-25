package com.example.powerai.engine.ai

import android.content.Context
import android.util.Log
import java.lang.reflect.Method
import kotlin.coroutines.Continuation

/**
 * Handles reflection-based interop with external AAR model engines.
 */
object LlamaReflectionBridge {
    private const val TAG = "LlamaReflectionBridge"

    fun getNativeLibInstance(context: Context): Any? {
        return try {
            val cls = Class.forName("com.mp.ai_core.NativeLib")
            val method = cls.getMethod("getInstance", Context::class.java)
            method.invoke(null, context)
        } catch (t: Throwable) {
            Log.d(TAG, "could not get NativeLib instance via reflection: ${t.message}")
            null
        }
    }

    fun defaultReturnValue(type: Class<*>): Any? {
        return when {
            type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java -> false
            type == java.lang.Integer.TYPE || type == java.lang.Integer::class.java -> 0
            type == java.lang.Long.TYPE || type == java.lang.Long::class.java -> 0L
            type == java.lang.Float.TYPE || type == java.lang.Float::class.java -> 0f
            type == java.lang.Double.TYPE || type == java.lang.Double::class.java -> 0.0
            else -> null
        }
    }

    // Additional reflection helpers can go here
}
