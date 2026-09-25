package com.example.powerai.engine.ai

import com.example.powerai.core.model.GenerationMetrics
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.PowerAIEngine
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.VectorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple factory that creates a PowerAIEngine for the requested type.
 */
object PowerAIEngineFactory {
    enum class EngineType { GEMMA, DEEPSEEK }

    fun create(
        type: EngineType,
        vectorRepository: VectorRepository,
        knowledgeRepository: KnowledgeRepository
    ): PowerAIEngine {
        return when (type) {
            EngineType.GEMMA -> object : PowerAIEngine {
                override val thinkingFlow: SharedFlow<String>? = null
                override val chunkFlow: SharedFlow<String>? = null
                override val metrics: StateFlow<GenerationMetrics> = MutableStateFlow(GenerationMetrics())

                override suspend fun init(provider: NativeResourceProvider) {}
                override suspend fun loadModel(modelPath: String, useGpu: Boolean): Boolean = true
                override suspend fun unload() {}
                override suspend fun isLoaded(): Boolean = true
                override suspend fun infer(prompt: String, maxTokens: Int, stream: Boolean): String = "[Gemma proxy]"
                override suspend fun setSystemPrompt(prompt: String): Boolean = true
                override suspend fun configureStreamingThinking(enabled: Boolean) {}
                override suspend fun stopGeneration(): Boolean = true
                override suspend fun configureRuntime(threadCount: Int, contextLength: Int) {}
                override suspend fun setOption(key: String, value: String): Boolean = true
                override suspend fun getStateData(): ByteArray? = null
                override suspend fun loadStateData(data: ByteArray?): Boolean = false
            }
            EngineType.DEEPSEEK -> {
                val rag = RAGDataCoordinator(vectorRepository, knowledgeRepository)
                DeepSeekEngine(rag)
            }
        }
    }
}
