package com.example.powerai.domain.model

/**
 * One navigable entry in the figures/tables tab of the PDF TOC sheet.
 */
data class PdfFigureItem(
    val id: String,
    val caption: String,
    val imageUri: String?,
    val subfolder: String?,
    val pageNumber: Int,
    val isTable: Boolean,
)
