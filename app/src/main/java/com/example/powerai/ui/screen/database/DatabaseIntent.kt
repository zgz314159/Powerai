package com.example.powerai.ui.screen.database

import com.example.powerai.core.model.KnowledgeItem

/**
 * Database 模块的所有用户意图
 */
sealed interface DatabaseIntent {
    /** 刷新数据 */
    data object Refresh : DatabaseIntent

    /** 加载全部数据 */
    data object LoadAll : DatabaseIntent

    /** 确保数据已加*/
    data object EnsureLoaded : DatabaseIntent

    /** 搜索 */
    data class Search(val query: String) : DatabaseIntent

    /** 聚焦到指定条*/
    data class FocusItem(val itemId: Long) : DatabaseIntent

    /** 聚焦到指定条目（带详情） */
    data class FocusItemWithDetails(val item: com.example.powerai.core.model.KnowledgeItem) : DatabaseIntent

    /** 消费焦点目标 */
    data object ConsumeFocusTarget : DatabaseIntent

    /** 聚焦分组 */
    data class FocusGroup(val groupKey: String) : DatabaseIntent

    /** 从抽屉聚焦分*/
    data class FocusGroupFromDrawer(val groupKey: String) : DatabaseIntent

    /** 消费焦点分组 */
    data object ConsumeFocusGroup : DatabaseIntent

    /** 选中条目 */
    data class SelectItem(val itemId: Long?) : DatabaseIntent

    /** 消费待滚动选中*/
    data object ConsumePendingSelectedItemScroll : DatabaseIntent

    /** 切换分组折叠状*/
    data class ToggleCollapsedGroup(val key: String) : DatabaseIntent

    /** 设置折叠分组 */
    data class SetCollapsedGroupKeys(val keys: Set<String>) : DatabaseIntent

    /** 清除搜索历史 */
    data object ClearSearchHistory : DatabaseIntent

    /** 刷新导入诊断 */
    data object RefreshImportDiagnostics : DatabaseIntent
}
