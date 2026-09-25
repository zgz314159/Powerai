package org.nehuatl.llamacpp

/**
 * Stub implementation of LlamaContext to allow compilation when the native library is missing.
 */
class LlamaContext(val id: Int, val params: Map<String, Any>) {
    fun setTokenCallback(callback: (String) -> Unit) {
        // stub
    }

    fun completion(params: Map<String, Any>): Map<String, Any> {
        // stub: return empty or error
        return emptyMap()
    }

    fun stopCompletion() {
        // stub
    }

    fun release() {
        // stub
    }
}
