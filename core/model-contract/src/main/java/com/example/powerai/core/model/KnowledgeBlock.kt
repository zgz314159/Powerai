package com.example.powerai.core.model

/**
 * MVP schema for structured blocks rendering.
 */
sealed interface KnowledgeBlock {
    val id: String?
    val boundingBox: String?
    val pageNumber: Int?
    val imageUri: String?
    val pdfWidth: Float?
    val pdfHeight: Float?
}

data class TextBlock(
    override val id: String?,
    val text: String,
    val style: TextStyle = TextStyle.Paragraph,
    val fontSize: Float? = null,
    val isBold: Boolean? = null,
    val alignment: String? = null,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock {
    enum class TextStyle { Paragraph, Heading1, Heading2, Heading3, Quote }
}

data class ImageBlock(
    override val id: String?,
    val src: String,
    val alt: String? = null,
    val caption: String? = null,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock

data class ListBlock(
    override val id: String?,
    val ordered: Boolean,
    val items: List<String>,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock

data class TableCell(
    val row: Int,
    val col: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val text: String,
    val isHeader: Boolean = false,
    val alignment: String? = null,
    val confidence: Float? = null,
    val boundingBox: String? = null
)

data class TableBlock(
    override val id: String?,
    val rows: List<List<String>>,
    val cells: List<TableCell>? = null,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock

data class CodeBlock(
    override val id: String?,
    val code: String,
    val language: String? = null,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock

data class FigureNodeBlock(
    override val id: String?,
    val label: String? = null,
    val caption: String? = null,
    val images: List<String> = emptyList(),
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock

data class UnknownBlock(
    override val id: String?,
    val type: String?,
    val rawText: String,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null,
    override val pdfWidth: Float? = null,
    override val pdfHeight: Float? = null
) : KnowledgeBlock
