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

    /** 真正重试失败/缺失的内置资产导入（不只是刷新诊断） */
    data object RetryAssetImport : DatabaseIntent

    /** 请求重建内置知识库：仅弹出确认，不删除任何数据 */
    data object RequestRebuildKnowledgeBase : DatabaseIntent

    /** 用户确认重建内置知识库 */
    data object ConfirmRebuildKnowledgeBase : DatabaseIntent

    /** 用户取消重建内置知识库 */
    data object CancelRebuildKnowledgeBase : DatabaseIntent

    /** 请求清空全部知识（第一步：说明影响范围，不删除任何数据） */
    data object RequestClearAllKnowledgeBase : DatabaseIntent

    /** 清空全部的第二步确认（进入最终确认） */
    data object ContinueClearAllKnowledgeBase : DatabaseIntent

    /** 最终确认清空全部知识（执行删除） */
    data object ConfirmClearAllKnowledgeBase : DatabaseIntent

    /** 取消清空全部知识 */
    data object CancelClearAllKnowledgeBase : DatabaseIntent

    /** 刷新用户知识库包列表 */
    data object RefreshUserPackages : DatabaseIntent

    /** 用所选目录导入或更新用户知识库包（同一目录内容未变化则跳过） */
    data class UpdateUserPackage(val treeUri: android.net.Uri) : DatabaseIntent

    /** 请求移除某个用户知识库包：仅弹出确认，不删除任何数据 */
    data class RequestRemoveUserPackage(val packageId: String) : DatabaseIntent

    /** 用户确认移除所选用户知识库包 */
    data object ConfirmRemoveUserPackage : DatabaseIntent

    /** 用户取消移除 */
    data object CancelRemoveUserPackage : DatabaseIntent
}

/**
 * Two-stage confirmation for the destructive "clear all knowledge" action: the user must first see
 * the impact scope and then explicitly confirm again before anything is deleted.
 */
enum class ClearAllConfirmStep {
    NONE,
    SCOPE,
    FINAL,
}
