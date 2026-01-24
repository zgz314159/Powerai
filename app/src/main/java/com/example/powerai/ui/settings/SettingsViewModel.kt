package com.example.powerai.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.data.importer.ImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: JsonRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val importProgress: StateFlow<ImportProgress?> = repo.importProgress

    private val _fontSize = MutableStateFlow(1.0f)
    val fontSize: StateFlow<Float> = _fontSize

    fun setFontSize(value: Float) {
        _fontSize.value = value.coerceIn(0.75f, 2.0f)
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            // derive display name from uri
            val name = uri.lastPathSegment ?: "imported"
            repo.importUri(uri, context.contentResolver, name)
        }
    }
}
