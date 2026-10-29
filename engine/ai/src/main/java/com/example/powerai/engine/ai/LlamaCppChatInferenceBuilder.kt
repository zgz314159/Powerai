package com.example.powerai.engine.ai

import net.ladenthin.llama.parameters.InferenceParameters
import net.ladenthin.llama.value.ChatMessage

/**
 * 将用户/资料正文通过库的 messages API 交给 GGUF `tokenizer.chat_template` 渲染一次。
 *
 * DeepSeek-R1 官方建议避免独立 system 角色：将运行时 system 字符串折叠进 **单条 user**，
 * 不另发 system message，也不在外层再手写 `<｜User｜>` 控制符。
 */
object LlamaCppChatInferenceBuilder {
    const val TEMPLATE_MODE = "gguf_chat_template_messages_v1"

    /** 与 Distill-R1 家族常用采样对齐；不作为事实正确性保证。 */
    const val DEFAULT_TEMPERATURE = 0.6f
    const val DEFAULT_TOP_P = 0.95f

    fun foldSystemIntoUser(
        systemPrompt: String?,
        userPrompt: String,
    ): String {
        val user = userPrompt.trim()
        val system = systemPrompt?.trim().orEmpty()
        if (system.isEmpty()) return user
        if (user.isEmpty()) return system
        return system + "\n\n" + user
    }

    fun buildMessages(
        systemPrompt: String?,
        userPrompt: String,
    ): List<ChatMessage> {
        val content = foldSystemIntoUser(systemPrompt, userPrompt)
        return listOf(ChatMessage("user", content))
    }

    fun buildInferenceParameters(
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int,
        stream: Boolean,
        temperature: Float = DEFAULT_TEMPERATURE,
        topP: Float = DEFAULT_TOP_P,
        seed: Int? = null,
    ): InferenceParameters {
        val messages = buildMessages(systemPrompt, userPrompt)
        var params =
            InferenceParameters.empty()
                .withMessages(messages)
                .withNPredict(maxTokens.coerceAtLeast(1))
                .withStream(stream)
                .withTemperature(temperature)
                .withTopP(topP)
                .withCachePrompt(false)
        if (seed != null) {
            params = params.withSeed(seed)
        }
        // 模板渲染后的角色边界停词；不手写另一模型的 ChatML 全文。
        params =
            params.withStopStrings(
                "<｜User｜>",
                "<｜end▁of▁sentence｜>",
                "</s>",
                "<|im_end|>",
                "<|endoftext|>",
            )
        return params
    }

    fun diagnosticsJson(
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int,
        stream: Boolean,
        temperature: Float = DEFAULT_TEMPERATURE,
        topP: Float = DEFAULT_TOP_P,
        seed: Int? = null,
        nThreads: Int,
        nCtx: Int,
        nBatch: Int,
        modelPath: String?,
    ): String {
        val folded = foldSystemIntoUser(systemPrompt, userPrompt)
        val systemFolded = !systemPrompt.isNullOrBlank()
        return buildString {
            append('{')
            append("\"runtime\":\"llama-android\",")
            append("\"templateMode\":\"").append(TEMPLATE_MODE).append("\",")
            append("\"messageRoles\":[\"user\"],")
            append("\"systemFoldedIntoUser\":").append(systemFolded).append(',')
            append("\"promptChars\":").append(folded.length).append(',')
            append("\"n_predict\":").append(maxTokens.coerceAtLeast(1)).append(',')
            append("\"stream\":").append(stream).append(',')
            append("\"temperature\":").append(temperature).append(',')
            append("\"top_p\":").append(topP).append(',')
            if (seed != null) append("\"seed\":").append(seed).append(',')
            append("\"n_threads\":").append(nThreads).append(',')
            append("\"n_ctx\":").append(nCtx).append(',')
            append("\"n_batch\":").append(nBatch).append(',')
            append("\"model\":\"").append(escapeJson(modelPath.orEmpty())).append('"')
            append('}')
        }
    }

    private fun escapeJson(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")
}
