package com.example.powerai.ui.jsonrepo

import com.example.powerai.data.json.JsonEntry

/**
 * JsonRepository 模块的用户意图
 */
sealed interface JsonRepoIntent {
    /** 加载文件列表 */
    data object LoadFiles : JsonRepoIntent

    /** 搜索条目 */
    data class SearchEntries(val fileId: String, val keyword: String, val pageSize: Int = 100, val page: Int = 0) : JsonRepoIntent

    /** 选择文件 */
    data class SelectFile(val fileId: String, val pageSize: Int = 100, val page: Int = 0) : JsonRepoIntent

    /** 更新条目 */
    data class UpdateEntry(val fileId: String, val entry: JsonEntry) : JsonRepoIntent

    /** 导出 JSON */
    data class ExportJson(val fileId: String, val targetPath: java.io.File, val callback: (Boolean) -> Unit) : JsonRepoIntent

    /** 导出 CSV */
    data class ExportCsv(val fileId: String, val targetPath: java.io.File, val callback: (Boolean) -> Unit) : JsonRepoIntent
}
