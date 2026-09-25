package com.example.powerai.ui.debug

import com.example.powerai.core.data.entity.KnowledgeEntity

/**
 * Debug 模块的用户意图
 */
sealed interface DebugIntent {
    /** 重新加载所有数*/
    data object ReloadAll : DebugIntent

    /** 更新条目 */
    data class UpdateEntry(val entity: KnowledgeEntity) : DebugIntent
}
