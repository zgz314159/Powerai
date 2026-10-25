package com.example.powerai.ui.screen.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Storage

@Suppress("DEPRECATION")
enum class MainBottomTab(val label: String) {
    DATABASE("数据"),
    LOCAL("本地"),
    AI("AI"),
    SMART("智能"),
    QUIZ("答题"),
    MINE("我的"),
    ;

    fun icon() =
        when (this) {
            DATABASE -> Icons.Default.Storage
            LOCAL -> Icons.Default.Folder
            AI -> Icons.Default.Chat
            SMART -> Icons.Default.AutoAwesome
            QUIZ -> Icons.Default.Quiz
            MINE -> Icons.Default.Person
        }
}
