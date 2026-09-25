package com.example.powerai.ui.screen.detail

/**
 * KnowledgeDetail 模块的用户意图
 */
sealed interface DetailIntent {
    /** 请求 Vision Boost */
    data class RequestVisionBoost(val entityId: Long, val blockId: String, val imageUri: String) : DetailIntent

    /** 应用 Vision Boost 到原*/
    data class ApplyVisionBoost(val entityId: Long, val rawBlockId: String, val cacheKey: String, val clearCacheAfter: Boolean) : DetailIntent

    /** 设置深度逻辑验证 */
    data class SetDeepLogicValidation(val enabled: Boolean) : DetailIntent

    /** 设置详情页字体缩*/
    data class SetDetailContentFontScale(val value: Float) : DetailIntent
}
