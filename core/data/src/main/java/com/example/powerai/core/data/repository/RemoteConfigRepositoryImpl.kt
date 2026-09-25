package com.example.powerai.core.data.repository

import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.core.data.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigRepositoryImpl @Inject constructor() : RemoteConfigRepository {
    override fun getAiApiKey(): String = BuildConfig.AI_API_KEY
    override fun getAiBaseUrl(): String = BuildConfig.AI_BASE_URL
    override fun getDeepSeekModel(): String = BuildConfig.DEEPSEEK_LOGIC_MODEL
    override fun isDebug(): Boolean = BuildConfig.DEBUG

    override fun getDebugStreamUrl(): String? {
        return try {
            val f = BuildConfig::class.java.getDeclaredField("DEBUG_STREAM_URL")
            f.isAccessible = true
            f.get(null) as? String
        } catch (_: Throwable) {
            null
        }
    }
}
