package com.example.powerai.data.settings

import android.content.Context
import androidx.lifecycle.asLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple settings store that persists font size and night mode.
 * Uses SharedPreferences but exposes StateFlow and LiveData for UI consumption.
 */
@Singleton
class FontSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("powerai_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_NIGHT_MODE = "night_mode"
        private const val DEFAULT_FONT = 1.0f
        private const val DEFAULT_NIGHT = false
    }

    private val _fontSize = MutableStateFlow(prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT))
    val fontSizeFlow: StateFlow<Float> = _fontSize
    val fontSizeLiveData = _fontSize.asLiveData()

    private val _nightMode = MutableStateFlow(prefs.getBoolean(KEY_NIGHT_MODE, DEFAULT_NIGHT))
    val nightModeFlow: StateFlow<Boolean> = _nightMode
    val nightModeLiveData = _nightMode.asLiveData()

    init {
        // Observe preference changes and keep flows in sync
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_FONT_SIZE -> _fontSize.value = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT)
                KEY_NIGHT_MODE -> _nightMode.value = prefs.getBoolean(KEY_NIGHT_MODE, DEFAULT_NIGHT)
            }
        }
    }

    fun setFontSize(size: Float) {
        val v = size.coerceIn(0.75f, 2.0f)
        prefs.edit().putFloat(KEY_FONT_SIZE, v).apply()
        _fontSize.value = v
    }

    fun setNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
        _nightMode.value = enabled
    }

    fun toggleNightMode() {
        setNightMode(!_nightMode.value)
    }
}
