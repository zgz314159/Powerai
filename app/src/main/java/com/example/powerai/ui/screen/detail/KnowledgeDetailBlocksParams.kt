package com.example.powerai.ui.screen.detail


import com.example.powerai.core.model.KnowledgeBlock
import androidx.compose.ui.Modifier
import com.example.powerai.util.PdfSourceRef

/**
 * 参数对象 - 内容显示相关参数
 * 包括：块数据、高亮、导航索引等UI显示所需信息
 */
data class BlocksContentDisplayParams(
    val blocks: List<KnowledgeBlock>,
    val highlight: String,
    val matchIndices: List<Int>,
    val initialBlockIndex: Int? = null,
    val initialBlockId: String? = null,
    val fontScale: Float = 1f,
    val enableSwipeNavigation: Boolean = true,
    val modifier: Modifier = Modifier
)

/**
 * 参数对象 - 文档资源相关参数
 * 包括：实体ID、PDF引用、图片URI映射
 */
data class BlocksDocumentResourceParams(
    val entityId: Long,
    val pageNumber: Int? = null,
    val imageUrisJson: String? = null,
    val pdfRef: PdfSourceRef.Ref? = null
)

/**
 * 参数对象 - 视觉增强相关状态和回调
 * 管理Vision Boost 相关的所有状态：
 * - 缓存markdown 结果
 * - 当前正在boost的块ID
 * - 错误状
 * - 相关回调
 */
data class BlocksVisionBoostingParams(
    val visionMarkdownByBlockId: Map<String, String> = emptyMap(),
    val visionBoostingBlockId: String? = null,
    val visionBoostErrorBlockId: String? = null,
    val visionBoostErrorMessage: String? = null,
    val onRequestVisionBoost: (blockId: String, imageUri: String) -> Unit = { _, _ -> },
    val onApplyVisionBoostToOriginal: (rawBlockId: String, cacheKey: String, clearCacheAfter: Boolean) -> Unit = { _, _, _ -> }
)

/**
 * 参数对象 - 深度逻辑验证相关参数
 */
data class BlocksValidationParams(
    val deepLogicValidationEnabled: Boolean = false,
    val onToggleDeepLogicValidation: (Boolean) -> Unit = {}
)

/**
 * 参数对象 - 交互回调（PDF打开、导航等
 */
data class BlocksInteractionParams(
    val onOpenPdfAtBox: (pageNumber: Int?, bboxJson: String?) -> Unit = { _, _ -> }
)
