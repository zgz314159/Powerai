package com.example.powerai.data.json

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.data.importer.DocxParser
import com.example.powerai.data.importer.ImportDefaults
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.importer.PdfParser
import com.example.powerai.data.importer.TxtParser
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 处理特定文件格式的导入逻辑。从 [JsonRepository.importUri] 提取以符合单一职责原则
 */
internal interface ImportFormatHandler {
    /**
     * 执行导入，返回生成的 fileId
     *
     * @param uri 源文URI
     * @param contentResolver Android ContentResolver
     * @param displayName 显示文件
     * @param batchSize 批处理大
     * @param onBatch 批处理回调，接收解析后的 KnowledgeEntity 列表
     * @param onProgress 进度回调，接ImportProgress 对象
     * @return 生成fileId，失败时为空字符
     */
    suspend fun import(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
        batchSize: Int,
        onBatch: suspend (List<KnowledgeEntity>) -> Unit,
        onProgress: (ImportProgress) -> Unit
    ): String
}

/**
 * 根据文件扩展名返回合适的 ImportFormatHandler
 */
internal object ImportFormatHandlerFactory {
    fun create(
        fileName: String,
        parser: FormatParserProvider
    ): ImportFormatHandler? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".txt") -> TxtImportHandler(parser)
            lower.endsWith(".pdf") -> PdfImportHandler(parser)
            lower.endsWith(".docx") || lower.endsWith(".doc") -> DocxImportHandler(parser)
            else -> null
        }
    }
}

/**
 * 提供 Parser 实例的接口，便于测试和依赖注入
 */
internal interface FormatParserProvider {
    fun txtParser(contentResolver: ContentResolver): TxtParser
    fun pdfParser(contentResolver: ContentResolver): PdfParser
    fun docxParser(contentResolver: ContentResolver): DocxParser
}

/**
 * TXT 格式导入处理
 */
private class TxtImportHandler(
    private val parserProvider: FormatParserProvider
) : ImportFormatHandler {
    override suspend fun import(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
        batchSize: Int,
        onBatch: suspend (List<KnowledgeEntity>) -> Unit,
        onProgress: (ImportProgress) -> Unit
    ): String {
        val parser = parserProvider.txtParser(contentResolver)
        return parser.parse(uri, displayName, batchSize, onBatch)
    }
}

/**
 * PDF 格式导入处理器（支持页面进度回调
 */
private class PdfImportHandler(
    private val parserProvider: FormatParserProvider
) : ImportFormatHandler {
    override suspend fun import(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
        batchSize: Int,
        onBatch: suspend (List<KnowledgeEntity>) -> Unit,
        onProgress: (ImportProgress) -> Unit
    ): String {
        val parser = parserProvider.pdfParser(contentResolver)
        return parser.parse(
            uri = uri,
            fileName = displayName,
            batchSize = batchSize,
            onBatchReady = onBatch,
            onProgressPages = { currentPage: Int, totalPages: Int ->
                val percent = if (totalPages > 0) (currentPage * 100 / totalPages) else 0
                onProgress(
                    ImportProgress(
                        fileId = "",
                        fileName = displayName,
                        totalItems = totalPages.toLong(),
                        importedItems = currentPage.toLong(),
                        percent = percent,
                        status = "in_progress"
                    )
                )
            }
        )
    }
}

/**
 * DOCX 格式导入处理
 */
private class DocxImportHandler(
    private val parserProvider: FormatParserProvider
) : ImportFormatHandler {
    override suspend fun import(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String,
        batchSize: Int,
        onBatch: suspend (List<KnowledgeEntity>) -> Unit,
        onProgress: (ImportProgress) -> Unit
    ): String {
        val parser = parserProvider.docxParser(contentResolver)
        return parser.parse(uri, displayName, batchSize, onBatch)
    }
}
