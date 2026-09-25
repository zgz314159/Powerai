package com.example.powerai.ui.screen.hybrid

/**
 * Hybrid 模块的所有用户意图
 */
sealed interface HybridIntent {
    /** 提交查询（默SMART 模式*/
    data class SubmitQuery(val question: String) : HybridIntent

    /** 提交查询（指定模式） */
    data class SubmitQueryWithMode(val question: String, val mode: com.example.powerai.ui.screen.main.DisplayMode) : HybridIntent

    /** 清除当前结果 */
    data object ClearResults : HybridIntent

    /** 设置 Web 搜索启用状*/
    data class SetWebSearchEnabled(val enabled: Boolean) : HybridIntent

    /** 导入文档 */
    data class ImportDocument(val uri: android.net.Uri) : HybridIntent

    /** 设置本地分页当前*/
    data class SetLocalCurrentPage(val page: Int) : HybridIntent

    /** 切换本地分组折叠 */
    data class ToggleLocalCollapsedGroup(val key: String) : HybridIntent

    /** 设置本地分组折叠 keys */
    data class SetLocalCollapsedGroupKeys(val keys: Set<String>) : HybridIntent

    /** 设置本地滚动位置 */
    data class SetLocalScrollPosition(val index: Int, val offset: Int) : HybridIntent

    /** 设置智能滚动位置 */
    data class SetSmartScrollPosition(val index: Int, val offset: Int) : HybridIntent

    /** 切换本地回答点赞 */
    data object ToggleLocalAnswerThumbUp : HybridIntent

    /** 切换本地回答*/
    data object ToggleLocalAnswerThumbDown : HybridIntent

    /** 记录智能搜索查询 */
    data class RecordSmartSearchQuery(val query: String) : HybridIntent

    /** 清除智能搜索历史 */
    data object ClearSmartSearchHistory : HybridIntent

    /** 清除本地搜索历史 */
    data object ClearLocalSearchHistory : HybridIntent
}
