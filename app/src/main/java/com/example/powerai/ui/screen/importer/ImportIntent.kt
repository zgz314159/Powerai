package com.example.powerai.ui.screen.importer

import android.net.Uri

/**
 * Import 模块的用户意图
 */
sealed interface ImportIntent {
    /** 导入文档 */
    data class ImportUri(val uri: Uri, val batchSize: Int = com.example.powerai.data.importer.ImportDefaults.DEFAULT_BATCH_SIZE) : ImportIntent
}
