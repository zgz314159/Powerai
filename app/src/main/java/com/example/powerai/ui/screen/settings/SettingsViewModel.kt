package com.example.powerai.ui.screen.settings

import com.example.powerai.core.data.dao.KnowledgeDao

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.data.settings.FontSettings
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import javax.inject.Inject

data class PdfImportDiagnostics(
    val fileId: String,
    val fileName: String,
    val rowsInDb: Int,
    val keywordHitCounts: Map<String, Int>,
    val samples: List<String>
)

data class SettingsUiState(
    val versionName: String = "?",
    val pdfDiagnostics: PdfImportDiagnostics? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: JsonRepository,
    private val knowledgeDao: KnowledgeDao,
    private val fontSettings: FontSettings,
    @ApplicationContext private val context: Context
) : BaseMviViewModel<SettingsIntent, SettingsUiState, Nothing>(
    initialState = SettingsUiState()
) {

    val importProgress: StateFlow<ImportProgress?> = repo.importProgress

    val detailContentFontScale: StateFlow<Float> = fontSettings.detailContentFontScaleFlow

    private val _devBackdoor = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val devBackdoor = _devBackdoor.asSharedFlow()

    private var devTapCount = 0
    private var lastDiagnosticsFileId: String? = null

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetDetailContentFontScale -> fontSettings.setDetailContentFontScale(intent.value)
            is SettingsIntent.OnVersionTapped -> onVersionTapped()
            is SettingsIntent.ImportUri -> importUri(intent.uri)
            is SettingsIntent.Shutdown -> shutdown()
        }
    }

    init {
        viewModelScope.launch {
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Throwable) { null } ?: "?"
            updateState { copy(versionName = version) }
        }

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

                updateState {
                    copy(pdfDiagnostics = PdfImportDiagnostics(
                        fileId = fileId,
                        fileName = fileName,
                        rowsInDb = rows,
                        keywordHitCounts = counts,
                        samples = samples
                    ))
                }
            }
        }
    }

    private fun onVersionTapped() {
        devTapCount++
        if (devTapCount >= 5) {
            devTapCount = 0
            _devBackdoor.tryEmit(Unit)
        }
    }

    private fun shutdown() {
        viewModelScope.cancel()
    }

    private fun importUri(uri: Uri) {
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
