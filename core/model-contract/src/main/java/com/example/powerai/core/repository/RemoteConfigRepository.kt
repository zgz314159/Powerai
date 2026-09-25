package com.example.powerai.core.repository

/**
 * Interface to provide remote service configurations without domain layer
 * depending on Android BuildConfig or Context.
 */
interface RemoteConfigRepository {
    fun getAiApiKey(): String
    fun getAiBaseUrl(): String
    fun getDeepSeekModel(): String
    fun isDebug(): Boolean
    fun getDebugStreamUrl(): String?
}
