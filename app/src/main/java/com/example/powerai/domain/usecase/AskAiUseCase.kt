package com.example.powerai.domain.usecase

import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.remote.api.AiRequest
import javax.inject.Inject

class AskAiUseCase @Inject constructor(private val api: AiApiService) {
    /**
     * 接收问题和本地参考内容，返回 AI 文本回答
     */
    suspend fun invoke(question: String, reference: String): String {
        val req = AiRequest(question = question, reference = reference)
        return try {
            api.askAi(req)
        } catch (t: Throwable) {
            "AI service error: ${t.message ?: t::class.simpleName}"
        }
    }
}
