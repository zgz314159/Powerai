package com.example.powerai.engine.ai

import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal object LlamaJniReflection {
    private const val TAG = "llama_jni_refl"
    private const val AAR_TAG = "AAR_INSPECTOR"

    @Volatile
    var nativeLibClassCache: Class<*>? = null
    @Volatile
    var aarStructureLogged: Boolean = false

    fun getNativeLibClass(): Class<*> {
        nativeLibClassCache?.let { return it }
        val cls = Class.forName("com.mp.ai_core.NativeLib")
        nativeLibClassCache = cls
        return cls
    }

    fun logAarStructureOnce(cls: Class<*>) {
        if (aarStructureLogged) return
        synchronized(this) {
            if (aarStructureLogged) return
            try {
                for (f in cls.declaredFields) {
                    try { Log.d(AAR_TAG, "declared field: ${f.name} static=${java.lang.reflect.Modifier.isStatic(f.modifiers)} type=${f.type}") } catch (_: Throwable) {}
                }
                for (m in cls.declaredMethods) {
                    try { Log.d(AAR_TAG, "declared method: ${LlamaJniDiagnostics.diagnosticMethodSignature(m)}") } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                // best effort only
            }
            aarStructureLogged = true
        }
    }

    fun createNamedCallbackProxy(interfaceType: Class<*>, callbackKind: String): Any {
        return Proxy.newProxyInstance(interfaceType.classLoader, arrayOf(interfaceType), InvocationHandler { proxy: Any, method: Method, args: Array<Any?>? ->
            try {
                when (method.name) {
                    "toString" -> return@InvocationHandler "$callbackKind-proxy(${interfaceType.name})"
                    "hashCode" -> return@InvocationHandler System.identityHashCode(proxy)
                    "equals" -> return@InvocationHandler proxy === args?.firstOrNull()
                    "asBinder" -> return@InvocationHandler null
                }

                if (callbackKind == "stream") {
                    when (method.name) {
                        "onToken", "onText", "onChunk" -> {
                            val text = args?.getOrNull(0)?.toString()
                            if (!text.isNullOrEmpty()) {
                                if (LlamaJniStreaming.shouldLogStreamPreview()) {
                                    Log.d(TAG, "typed stream callback ${method.name} -> ${LlamaJniStreaming.buildCallbackTokenPreview(text)}")
                                }
                                LlamaJni.onNativeChunk(text)
                            }
                        }
                        "onToolCall" -> {
                            val name = args?.getOrNull(0)?.toString().orEmpty()
                            val payload = args?.getOrNull(1)?.toString().orEmpty()
                            Log.d(TAG, "typed stream callback onToolCall -> $name")
                            LlamaJni.onNativeChunk("\n[tool:$name] $payload\n")
                        }
                        "onError" -> {
                            val message = args?.getOrNull(0)?.toString() ?: "unknown stream error"
                            Log.d(TAG, "typed stream callback onError -> $message")
                            LlamaJni.onNativeChunk("[stream error] $message")
                            LlamaJni.onNativeFinish()
                        }
                        "onDone", "onFinish", "onComplete" -> {
                            Log.d(TAG, "typed stream callback ${method.name}")
                            LlamaJni.onNativeFinish()
                        }
                    }
                } else if (method.name == "onDone") {
                    Log.d(TAG, "typed done callback onDone")
                    LlamaJni.onNativeFinish()
                }
            } catch (t: Throwable) {
                Log.d(TAG, "$callbackKind callback proxy error: ${t.message}")
            }
            defaultReturnValue(method.returnType)
        })
    }

    fun defaultReturnValue(returnType: Class<*>): Any? {
        return when {
            returnType == java.lang.Void.TYPE -> null
            returnType == java.lang.Boolean.TYPE -> false
            returnType == java.lang.Integer.TYPE -> 0
            returnType == java.lang.Long.TYPE -> 0L
            returnType == java.lang.Float.TYPE -> 0f
            returnType == java.lang.Double.TYPE -> 0.0
            returnType == java.lang.Short.TYPE -> 0.toShort()
            returnType == java.lang.Byte.TYPE -> 0.toByte()
            returnType == java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    fun getNativeLibInstance(appContext: android.content.Context?): Any? {
        try {
            val cls = getNativeLibClass()
            logAarStructureOnce(cls)

            fun unwrapMapForInstance(mapObj: Any): Any? {
                try {
                    val valuesMethod = mapObj.javaClass.getMethod("values")
                    val valuesObj = valuesMethod.invoke(mapObj)
                    val iterMethod = valuesObj.javaClass.getMethod("iterator")
                    val iter = iterMethod.invoke(valuesObj) as java.util.Iterator<*>
                    while (iter.hasNext()) {
                        val candidate = iter.next()
                        if (candidate == null) continue
                        try {
                            candidate.javaClass.getMethod("nativeLoadStateFile", String::class.java)
                            return candidate
                        } catch (_: Throwable) {
                            try {
                                if (candidate is java.util.Map<*, *>) {
                                    val nested = unwrapMapForInstance(candidate)
                                    if (nested != null) return nested
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
                return null
            }

            val staticFieldNames = listOf("INSTANCE", "instance", "sInstance", "instances")
            for (name in staticFieldNames) {
                try {
                    val f = cls.getDeclaredField(name)
                    f.isAccessible = true
                    if (java.lang.reflect.Modifier.isStatic(f.modifiers)) {
                        val v = f.get(null)
                        if (v != null) {
                            if (v is java.util.Map<*, *>) {
                                val found = unwrapMapForInstance(v)
                                if (found != null) {
                                    LlamaJniDiagnostics.attemptDiagnosticLoadOnce(found)
                                    return found
                                }
                                try {
                                    val fallback = (v as java.util.Map<*, *>).values().iterator().asSequence().firstOrNull()
                                    if (fallback != null) {
                                        LlamaJniDiagnostics.attemptDiagnosticLoadOnce(fallback)
                                        return fallback
                                    }
                                } catch (_: Throwable) {}
                            } else {
                                LlamaJniDiagnostics.attemptDiagnosticLoadOnce(v)
                                return v
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            val staticMethodNames = listOf("getInstance", "create", "getInstances", "get", "access\$getInstances\$cp")
            for (name in staticMethodNames) {
                try {
                    val m = try { cls.getDeclaredMethod(name) } catch (e: Throwable) { null }
                    if (m != null) {
                        m.isAccessible = true
                        val inst = m.invoke(null)
                        if (inst != null) {
                            if (inst is java.util.Map<*, *>) {
                                val found = unwrapMapForInstance(inst)
                                if (found != null) {
                                    LlamaJniDiagnostics.attemptDiagnosticLoadOnce(found)
                                    return found
                                }
                                try {
                                    val fallback = (inst as java.util.Map<*, *>).values().iterator().asSequence().firstOrNull()
                                    if (fallback != null) {
                                        LlamaJniDiagnostics.attemptDiagnosticLoadOnce(fallback)
                                        return fallback
                                    }
                                } catch (_: Throwable) {}
                            } else {
                                LlamaJniDiagnostics.attemptDiagnosticLoadOnce(inst)
                                return inst
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            try {
                val ctx = appContext
                for (ctor in cls.declaredConstructors) {
                    try {
                        ctor.isAccessible = true
                        val params = ctor.parameterTypes
                        if (params.isNotEmpty()) {
                            val args = Array<Any?>(params.size) { idx ->
                                when (val p = params[idx]) {
                                    android.content.Context::class.java -> ctx
                                    String::class.java -> "deepseek_session"
                                    java.lang.Integer.TYPE, Integer::class.java -> 0
                                    java.lang.Float.TYPE, java.lang.Float::class.java -> 0f
                                    java.lang.Double.TYPE, java.lang.Double::class.java -> 0.0
                                    else -> null
                                }
                            }
                            val inst = try { ctor.newInstance(*args) } catch (_: Throwable) { null }
                            if (inst != null) {
                                LlamaJniDiagnostics.attemptDiagnosticLoadOnce(inst)
                                return inst
                            }
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}

            try {
                val ctor = cls.getDeclaredConstructor()
                ctor.isAccessible = true
                val inst = try { ctor.newInstance() } catch (_: Throwable) { null }
                if (inst != null) {
                    LlamaJniDiagnostics.attemptDiagnosticLoadOnce(inst)
                    return inst
                }
            } catch (_: Throwable) {}

            try {
                for (m in cls.declaredMethods) {
                    try {
                        if (java.lang.reflect.Modifier.isStatic(m.modifiers) && m.parameterCount == 0 && m.returnType == cls) {
                            m.isAccessible = true
                            val inst = try { m.invoke(null) } catch (_: Throwable) { null }
                            if (inst != null) return inst
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}

            try {
                val f = cls.getDeclaredField("INSTANCE")
                f.isAccessible = true
                val inst = try { f.get(null) } catch (_: Throwable) { null }
                if (inst != null) return inst
            } catch (_: Throwable) {}

            try {
                for (f in cls.declaredFields) {
                    try {
                        if (java.lang.reflect.Modifier.isStatic(f.modifiers) && f.type == cls) {
                            f.isAccessible = true
                            val inst = f.get(null)
                            if (inst != null) return inst
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}

            try {
                val compCls = Class.forName("com.mp.ai_core.NativeLib\$Companion")
                try {
                    val f = compCls.getDeclaredField("INSTANCE")
                    f.isAccessible = true
                    val compInst = f.get(null)
                    if (compInst != null) {
                        try {
                            val gm = compCls.getMethod("getInstance")
                            val inst = gm.invoke(compInst)
                            if (inst != null) return inst
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}

        } catch (t: Throwable) {
            Log.d(AAR_TAG, "getNativeLibInstance failed: ${t.message}")
        }
        return null
    }

    fun ensureNativeInstance(appContext: android.content.Context?, currentInstance: Any?): Any? {
        if (currentInstance != null) return currentInstance
        try {
            val cls = getNativeLibClass()
            try {
                val f = cls.getDeclaredField("instances")
                f.isAccessible = true
                val map = f.get(null)
                if (map is java.util.Map<*, *>) {
                    val first = map.values().iterator().asSequence().firstOrNull()
                    if (first != null) {
                        Log.d(AAR_TAG, "got NativeLib instance from instances map")
                        return first
                    }
                }
            } catch (_: Throwable) {}

            try {
                val ctx = appContext
                for (ctor in cls.declaredConstructors) {
                    try {
                        ctor.isAccessible = true
                        val params = ctor.parameterTypes
                        if (params.isNotEmpty()) {
                            val args = Array<Any?>(params.size) { idx ->
                                when (val p = params[idx]) {
                                    android.content.Context::class.java -> ctx
                                    String::class.java -> "deepseek_session"
                                    java.lang.Integer.TYPE, Integer::class.java -> 0
                                    java.lang.Float.TYPE, java.lang.Float::class.java -> 0f
                                    java.lang.Double.TYPE, java.lang.Double::class.java -> 0.0
                                    else -> null
                                }
                            }
                            val inst = try { ctor.newInstance(*args) } catch (_: Throwable) { null }
                            if (inst != null) return inst
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}

            val factoryNames = listOf("getInstance", "create")
            for (name in factoryNames) {
                try {
                    val m = cls.getDeclaredMethod(name)
                    m.isAccessible = true
                    val inst = m.invoke(null)
                    if (inst != null) return inst
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        return null
    }
}
