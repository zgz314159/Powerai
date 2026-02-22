package com.example.powerai.ui.blocks

/**
 * MVP schema for structured blocks rendering.
 *
 * This is intentionally tolerant and minimal: JSON parsing should accept multiple shapes.
 */
sealed interface KnowledgeBlock {
    val id: String?

    // Optional bounding box metadata for this block (JSON string), e.g. {"xMin":..,"yMin":..,"xMax":..,"yMax":..}
    val boundingBox: String?

    // Optional 1-based page number for this block (preferred over entity.pageNumber when present).
    val pageNumber: Int?

    // Optional snapshot image URI that corresponds to this block (enables 100% accurate “view original”).
    val imageUri: String?
}

data class TextBlock(
    override val id: String?,
    val text: String,
    val style: TextStyle = TextStyle.Paragraph,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null
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
    override val imageUri: String? = null
) : KnowledgeBlock

data class ListBlock(
    override val id: String?,
    val ordered: Boolean,
    val items: List<String>,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null
) : KnowledgeBlock

data class TableBlock(
    override val id: String?,
    val rows: List<List<String>>,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null
) : KnowledgeBlock

data class CodeBlock(
    override val id: String?,
    val code: String,
    val language: String? = null,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null
) : KnowledgeBlock

data class UnknownBlock(
    override val id: String?,
    val type: String?,
    val rawText: String,
    override val boundingBox: String? = null,
    override val pageNumber: Int? = null,
    override val imageUri: String? = null
) : KnowledgeBlock
