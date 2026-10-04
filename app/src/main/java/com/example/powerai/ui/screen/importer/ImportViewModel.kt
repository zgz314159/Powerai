package com.example.powerai.ui.screen.importer

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.PdfPromptInfo
import com.example.powerai.data.importer.UserKbImportResult
import com.example.powerai.data.importer.UserKbPackageImporter
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 用户目录知识库导入界面状态：导入中、成功、未变化跳过、失败与取消都清晰可辨。 */
sealed interface KnowledgeImportUiState {
    data object Idle : KnowledgeImportUiState

    data class Running(
        val displayName: String,
        val importedItems: Long,
    ) : KnowledgeImportUiState

    data class Success(
        val entries: Int,
        val blocks: Int,
        val assets: Int,
        val displayName: String,
        val pdf: PdfPromptInfo?,
    ) : KnowledgeImportUiState

    data class Skipped(
        val entries: Int,
        val displayName: String,
        val pdf: PdfPromptInfo?,
    ) : KnowledgeImportUiState

    data class Failed(
        val reason: String,
    ) : KnowledgeImportUiState

    data object Cancelled : KnowledgeImportUiState
}

@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val importer: UserKbPackageImporter,
    ) : BaseMviViewModel<ImportIntent, KnowledgeImportUiState, Nothing>(
            initialState = KnowledgeImportUiState.Idle,
        ) {
        override fun onIntent(intent: ImportIntent) {
            when (intent) {
                is ImportIntent.ImportDirectory -> importDirectory(intent.treeUri)
                ImportIntent.Reset -> updateState { KnowledgeImportUiState.Idle }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun importDirectory(treeUri: Uri) {
            updateState { KnowledgeImportUiState.Running(displayName = "", importedItems = 0) }
            viewModelScope.launch {
                try {
                    val result =
                        importer.importDirectory(treeUri) { progress ->
                            updateState {
                                KnowledgeImportUiState.Running(progress.displayName, progress.importedItems)
                            }
                        }
                    updateState { result.toUiState() }
                } catch (cancellation: CancellationException) {
                    updateState { KnowledgeImportUiState.Cancelled }
                    throw cancellation
                } catch (error: Throwable) {
                    updateState { KnowledgeImportUiState.Failed(error.message ?: "导入失败") }
                }
            }
        }
    }

private fun UserKbImportResult.toUiState(): KnowledgeImportUiState =
    when (this) {
        is UserKbImportResult.Imported ->
            KnowledgeImportUiState.Success(entries, blocks, assets, displayName, pdf)
        is UserKbImportResult.Skipped ->
            KnowledgeImportUiState.Skipped(entries, displayName, pdf)
        is UserKbImportResult.Failed ->
            KnowledgeImportUiState.Failed(reason)
    }
