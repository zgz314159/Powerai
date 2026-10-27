package com.example.powerai.ui.screen.pdf

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional controller for driving PDF highlights from outside the screen.
 *
 * Usage:
 * - controller.highlightBox(pageNumber = 12, bboxJson = "{...}")
 * - Pass [highlightPage] / [highlightBboxJson] from the controller into PdfViewerScreen.
 */
class PdfHighlightController {
    private val _highlightPage = MutableStateFlow<Int?>(null)
    val highlightPage: StateFlow<Int?> = _highlightPage.asStateFlow()

    private val _highlightBboxJson = MutableStateFlow<String?>(null)
    val highlightBboxJson: StateFlow<String?> = _highlightBboxJson.asStateFlow()

    fun highlightBox(pageNumber: Int?, bboxJson: String?) {
        _highlightPage.value = pageNumber
        _highlightBboxJson.value = bboxJson
    }

    fun clear() {
        _highlightPage.value = null
        _highlightBboxJson.value = null
    }
}
