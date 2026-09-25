package com.example.powerai.ui.screen.importer

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val progress: ImportProgress? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: DocumentImportManager
) : BaseMviViewModel<ImportIntent, ImportUiState, Nothing>(
    initialState = ImportUiState()
) {

    override fun onIntent(intent: ImportIntent) {
        when (intent) {
            is ImportIntent.ImportUri -> importUri(intent.uri, intent.batchSize)
        }
    }

    private fun importUri(uri: Uri, batchSize: Int) {
        viewModelScope.launch {
            importer.importUri(uri, batchSize)
        }
    }
}
