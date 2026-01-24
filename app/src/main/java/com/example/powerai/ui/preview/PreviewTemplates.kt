package com.example.powerai.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.theme.PowerAiTheme

/**
 * Safe preview helpers.
 *
 * Use `SafePreview` as a wrapper for @Preview functions to avoid calling
 * composables that perform Hilt/ViewModel injection. Keep previews static
 * and pass sample content into `SafePreview`.
 */
@Composable
fun SafePreview(content: @Composable () -> Unit) {
    PowerAiTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExampleSafePreview() {
    SafePreview {
        Text("Example safe preview", style = MaterialTheme.typography.titleLarge)
    }
}
