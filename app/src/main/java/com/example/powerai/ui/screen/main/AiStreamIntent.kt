package com.example.powerai.ui.screen.main

/**
 * AiStream 模块的用户意图
 */
sealed interface AiStreamIntent {
    /** 发AI 流式请求 */
    data class AskAiStream(val userInput: String, val webSearchEnabled: Boolean) : AiStreamIntent

    /** 停止*/
    data object StopStream : AiStreamIntent

    /** 选择会话 */
    data class SelectSession(val sessionId: Long) : AiStreamIntent

    /** 新建会话 */
    data object NewSession : AiStreamIntent
}
