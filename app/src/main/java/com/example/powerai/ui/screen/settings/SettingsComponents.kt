@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsActionRow(label: String, actionLabel: String = "执行", onAction: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
            Text(actionLabel)
        }
    }
}
