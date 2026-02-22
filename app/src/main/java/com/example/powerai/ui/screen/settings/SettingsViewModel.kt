package com.example.powerai.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.data.local.dao.KnowledgeDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: JsonRepository,
    private val knowledgeDao: KnowledgeDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val importProgress: StateFlow<ImportProgress?> = repo.importProgress

    private val _fontSize = MutableStateFlow(1.0f)
    val fontSize: StateFlow<Float> = _fontSize

    data class PdfImportDiagnostics(
        val fileId: String,
        val fileName: String,
        val rowsInDb: Int,
        val keywordHitCounts: Map<String, Int>,
        val samples: List<String>
    )

    private val _pdfDiagnostics = MutableStateFlow<PdfImportDiagnostics?>(null)
    val pdfDiagnostics: StateFlow<PdfImportDiagnostics?> = _pdfDiagnostics

    private var lastDiagnosticsFileId: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.importProgress.collect { p ->
                if (p == null) return@collect
                if (p.status != "imported") return@collect
                val fileId = p.fileId
                if (fileId.isBlank()) return@collect
                val fileName = p.fileName
                if (!fileName.lowercase().endsWith(".pdf")) return@collect
                if (fileId == lastDiagnosticsFileId) return@collect
                lastDiagnosticsFileId = fileId

                val sourcePrefix = "pdf:$fileId::"
                val rows = try { knowledgeDao.countBySourcePrefix(sourcePrefix) } catch (_: Throwable) { -1 }

                val keywords = listOf("回路", "电缆槽")
                val counts = LinkedHashMap<String, Int>(keywords.size)
                for (kw in keywords) {
                    val kwNoSpace = TextSanitizer.normalizeForSearch(kw).replace(Regex("\\s+"), "")
                    val c = try { knowledgeDao.countMatchesBySourcePrefix(sourcePrefix, kwNoSpace) } catch (_: Throwable) { -1 }
                    counts[kw] = c
                }

                val sampleEntities = try { knowledgeDao.sampleBySourcePrefix(sourcePrefix, limit = 3) } catch (_: Throwable) { emptyList() }
                val samples = sampleEntities.map { it.content.replace('\n', ' ').take(120) }

                _pdfDiagnostics.value = PdfImportDiagnostics(
                    fileId = fileId,
                    fileName = fileName,
                    rowsInDb = rows,
                    keywordHitCounts = counts,
                    samples = samples
                )
            }
        }
    }

    fun setFontSize(value: Float) {
        _fontSize.value = value.coerceIn(0.75f, 2.0f)
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val name = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                } ?: uri.lastPathSegment
            } catch (_: Exception) {
                uri.lastPathSegment
            } ?: "imported"

            repo.importUri(uri, context.contentResolver, name)
        }
    }
}
