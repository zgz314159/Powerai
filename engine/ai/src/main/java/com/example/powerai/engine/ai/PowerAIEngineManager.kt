package com.example.powerai.engine.ai

import com.example.powerai.core.repository.KnowledgeRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 单例管理器：确保同时只激活一个重资源模型引擎（避免占用过多内显存）
 */
object PowerAIEngineManager {
    private val mutex = Mutex()
    private var activeEngine: PowerAIEngine? = null
    private var activeType: PowerAIEngineFactory.EngineType? = null

    suspend fun switchTo(
        type: PowerAIEngineFactory.EngineType,
        provider: NativeResourceProvider,
        vectorRepo: com.example.powerai.core.repository.VectorRepository,
        knowledgeRepo: com.example.powerai.core.repository.KnowledgeRepository,
        modelPath: String,
        useGpu: Boolean = false
    ): PowerAIEngine {
        return mutex.withLock {
            if (activeType == type && activeEngine?.isLoaded() == true) {
                return@withLock activeEngine!!
            }

            try {
                withContext(Dispatchers.IO) {
                    activeEngine?.unload()
                }
            } catch (_: Throwable) {}
            activeEngine = null
            activeType = null

            val engine = PowerAIEngineFactory.create(type, vectorRepo, knowledgeRepo)
            withContext(Dispatchers.IO) {
                engine.init(provider)
            }
            val ok = withContext(Dispatchers.IO) {
                engine.loadModel(modelPath, useGpu)
            }
            if (!ok) {
                var detail: String? = null
                try {
                    if (type == PowerAIEngineFactory.EngineType.DEEPSEEK && engine is DeepSeekEngine) {
                        detail = engine.getLastError()
                    }
                } catch (_: Throwable) {}
                val msg = if (detail.isNullOrBlank()) "failed to load model: $modelPath" else "failed to load model: $modelPath; cause: $detail"
                throw IllegalStateException(msg)
            }
            activeEngine = engine
            activeType = type
            engine
        }
    }

    suspend fun closeActive() {
        mutex.withLock {
            try {
                withContext(Dispatchers.IO) {
                    activeEngine?.unload()
                }
            } catch (_: Throwable) {}
            activeEngine = null
            activeType = null
        }
    }
}
