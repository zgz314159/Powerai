package com.example.powerai.ui.screen.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.DocumentImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: DocumentImportManager
) : ViewModel() {

    val progress: StateFlow<com.example.powerai.data.importer.ImportProgress?> = importer.progress

    fun importUri(uri: Uri, batchSize: Int = com.example.powerai.data.importer.ImportDefaults.DEFAULT_BATCH_SIZE) {
        viewModelScope.launch {
            importer.importUri(uri, batchSize)
        }
    }
}
