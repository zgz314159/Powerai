package com.example.powerai.engine.ai

import android.util.Log
import java.lang.reflect.Method

data class NativeRuntimeDiagnostics(
    val requestedBackend: String?,
    val effectiveBackend: String,
    val gpuOffloadActive: Boolean,
    val backendInitOpencl: Boolean,
    val backendInitVulkan: Boolean,
    val configuredThreadCount: Int,
    val configuredContextLength: Int,
    val lastInitParamsJson: String?,
    val lastRequestedGpuLayers: Int?,
    val modelInfo: String?
)

internal object LlamaJniDiagnostics {
    private const val TAG = "llama_jni_diag"
    private const val AAR_TAG = "AAR_INSPECTOR"

    @Volatile
    var backendInitOpencl: Boolean = false
    @Volatile
    var backendInitVulkan: Boolean = false
    @Volatile
    var requestedBackend: String? = null
    @Volatile
    var lastInitParamsJson: String? = null
    @Volatile
    var lastRequestedGpuLayers: Int? = null
    @Volatile
    var diagnosticLoadDone: Boolean = false

    fun diagnosticTypeName(type: Class<*>): String {
        return when (type) {
            java.lang.Integer.TYPE -> "int"
            java.lang.Long.TYPE -> "long"
            java.lang.Boolean.TYPE -> "boolean"
            java.lang.Float.TYPE -> "float"
            java.lang.Double.TYPE -> "double"
            java.lang.Short.TYPE -> "short"
            java.lang.Byte.TYPE -> "byte"
            java.lang.Character.TYPE -> "char"
            java.lang.Void.TYPE -> "void"
            else -> type.name
        }
    }

    fun diagnosticMethodSignature(method: Method): String {
        val params = method.parameterTypes.joinToString(", ") { diagnosticTypeName(it) }
        return "${method.name}($params): ${diagnosticTypeName(method.returnType)}"
    }

    fun extractSystemDescriptor(modelInfo: String?): String? {
        if (modelInfo.isNullOrBlank()) return null
        val marker = "\"system\":\""
        val start = modelInfo.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = modelInfo.indexOf('"', from)
        if (end <= from) return null
        return modelInfo.substring(from, end)
    }

    fun inferEffectiveBackend(requested: String?, modelInfo: String?): Pair<String, Boolean> {
        val normalizedRequested = requested?.lowercase()
        val systemDescriptor = extractSystemDescriptor(modelInfo)?.uppercase()
        val gpuHints = listOf("VULKAN", "OPENCL", "GPU", "CUDA", "METAL", "CLBLAST")
        val gpuActive = gpuHints.any { hint -> systemDescriptor?.contains(hint) == true }
        val effectiveBackend = when {
            gpuActive && normalizedRequested == "vulkan" -> "vulkan"
            gpuActive && normalizedRequested == "opencl" -> "opencl"
            systemDescriptor?.contains("CPU") == true -> "cpu"
            !normalizedRequested.isNullOrBlank() -> normalizedRequested
            else -> "unknown"
        }
        return effectiveBackend to gpuActive
    }

    @Synchronized
    fun attemptDiagnosticLoadOnce(instance: Any) {
        if (diagnosticLoadDone) return
        diagnosticLoadDone = true
        Log.d(AAR_TAG, "Diagnostic load disabled to avoid crashes")
    }

    fun getNativeRuntimeDiagnostics(
        configuredThreadCount: Int,
        configuredContextLength: Int,
        modelInfo: String?
    ): NativeRuntimeDiagnostics {
        val (effectiveBackend, gpuOffloadActive) = inferEffectiveBackend(
            requested = requestedBackend,
            modelInfo = modelInfo
        )
        return NativeRuntimeDiagnostics(
            requestedBackend = requestedBackend,
            effectiveBackend = effectiveBackend,
            gpuOffloadActive = gpuOffloadActive,
            backendInitOpencl = backendInitOpencl,
            backendInitVulkan = backendInitVulkan,
            configuredThreadCount = configuredThreadCount,
            configuredContextLength = configuredContextLength,
            lastInitParamsJson = lastInitParamsJson,
            lastRequestedGpuLayers = lastRequestedGpuLayers,
            modelInfo = modelInfo
        )
    }
}
