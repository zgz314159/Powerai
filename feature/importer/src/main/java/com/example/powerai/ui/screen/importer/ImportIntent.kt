package com.example.powerai.ui.screen.importer

import android.net.Uri

/**
 * 用户知识库目录导入的意图。
 */
sealed interface ImportIntent {
    /** 用户通过系统目录选择器选中的知识库输出目录。 */
    data class ImportDirectory(val treeUri: Uri) : ImportIntent

    /** 清除当前结果，回到初始状态以便重试。 */
    data object Reset : ImportIntent
}
