package com.example.powerai.ui.screen.settings

import android.net.Uri

/**
 * Settings 模块的用户意图
 */
sealed interface SettingsIntent {
    /** 设置详情页字体缩*/
    data class SetDetailContentFontScale(val value: Float) : SettingsIntent

    /** 版本号点击（开发者后门） */
    data object OnVersionTapped : SettingsIntent

    /** 导入 URI */
    data class ImportUri(val uri: Uri) : SettingsIntent

    /** 关闭 ViewModel */
    data object Shutdown : SettingsIntent
}
