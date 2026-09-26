package com.example.powerai.di

import com.example.powerai.BuildConfig
import com.example.powerai.core.repository.RemoteConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Composition-root implementation of the runtime AI configuration contract.
 *
 * The app BuildConfig is the single authoritative source for the AI endpoint,
 * API key and model. Core and engine modules only ever see
 * [RemoteConfigRepository], so they no longer depend on their own empty
 * BuildConfig fields. The values are plain constructor data so tests can
 * exercise configured and unconfigured states with fakes, and the class is
 * deliberately not a data class so string representations can never contain
 * credentials.
 */
class AiRuntimeConfig(
    private val baseUrl: String,
    private val apiKey: String,
    private val deepSeekModel: String,
    private val debug: Boolean,
    private val debugStreamUrl: String?,
) : RemoteConfigRepository {
    override fun getAiBaseUrl(): String = baseUrl

    override fun getAiApiKey(): String = apiKey

    override fun getDeepSeekModel(): String = deepSeekModel

    override fun isDebug(): Boolean = debug

    override fun getDebugStreamUrl(): String? = debugStreamUrl
}

@Module
@InstallIn(SingletonComponent::class)
object AiConfigModule {
    @Provides
    @Singleton
    fun provideRemoteConfigRepository(): RemoteConfigRepository =
        AiRuntimeConfig(
            baseUrl = BuildConfig.AI_BASE_URL,
            apiKey = BuildConfig.AI_API_KEY,
            deepSeekModel = BuildConfig.DEEPSEEK_LOGIC_MODEL,
            debug = BuildConfig.DEBUG,
            debugStreamUrl = readOptionalStaticField("DEBUG_STREAM_URL"),
        )

    /**
     * Mirrors the previous behaviour: the optional debug stream URL only
     * exists when it was declared as a BuildConfig field, and absent fields
     * resolve to null instead of failing.
     */
    private fun readOptionalStaticField(name: String): String? {
        return try {
            val field = BuildConfig::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.get(null) as? String
        } catch (_: Throwable) {
            null
        }
    }
}
