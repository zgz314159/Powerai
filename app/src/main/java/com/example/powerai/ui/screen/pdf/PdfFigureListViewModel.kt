package com.example.powerai.ui.screen.pdf

import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.data.dao.KnowledgeDao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.ui.blocks.BlocksParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One navigable entry in the "鍥捐〃" (figures/tables) tab of the PDF TOC sheet. */
data class PdfFigureItem(
    val id: String,
    val caption: String,
    val imageUri: String?,
    val subfolder: String?,
    val pageNumber: Int,
    val isTable: Boolean
)

@HiltViewModel
class PdfFigureListViewModel @Inject constructor(
    private val dao: KnowledgeDao
) : ViewModel() {

    private val _figures = MutableStateFlow<List<PdfFigureItem>>(emptyList())
    val figures: StateFlow<List<PdfFigureItem>> = _figures

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var loadedForFileId: String? = null

    fun loadFor(fileId: String) {
        if (fileId.isBlank() || loadedForFileId == fileId) return
        loadedForFileId = fileId
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) {
                loadPdfFigures(dao, fileId)
            }
            _figures.value = items
            _isLoading.value = false
        }
    }

    private suspend fun loadPdfFigures(dao: KnowledgeDao, fileId: String): List<PdfFigureItem> {
        val sourcePrefix = "assets/kb/${fileId.lowercase()}"
        val rows = dao.sampleBySourcePrefix(sourcePrefix, 5000)
        if (rows.isEmpty()) return emptyList()

        val out = ArrayList<PdfFigureItem>()
        val seenImageUris = HashSet<String>()

        for (entity in rows) {
            val blocks = BlocksParser.parseBlocks(entity.contentBlocksJson) ?: continue
            for (block in blocks) {
                val (caption, imageUri, isTable) = when (block) {
                    is ImageBlock -> Triple(block.caption?.takeIf { it.isNotBlank() }, block.imageUri ?: block.src, false)
                    is TableBlock -> Triple(null, block.imageUri, true)
                    else -> continue
                }
                if (imageUri.isNullOrBlank()) continue
                if (!seenImageUris.add(imageUri)) continue
                val page = block.pageNumber ?: entity.pageNumber ?: continue
                out.add(
                    PdfFigureItem(
                        id = block.id ?: imageUri,
                        caption = caption ?: if (isTable) "表格 · 第 $page 页" else "图 · 第 $page 页",
                        imageUri = imageUri,
                        subfolder = entity.source,
                        pageNumber = page,
                        isTable = isTable
                    )
                )
            }
        }
        return out.sortedBy { it.pageNumber }
    }
}
