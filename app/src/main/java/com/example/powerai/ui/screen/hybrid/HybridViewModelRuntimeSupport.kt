package com.example.powerai.ui.screen.hybrid

import android.content.Context
import android.net.Uri
import com.example.powerai.data.importer.DocumentImportManager

internal class HybridViewModelRuntimeSupport(
    private val importer: DocumentImportManager,
    private val context: Context
) {

    fun importDocument(
        uri: Uri,
        currentQuestion: String,
        onFollowUpQuery: (String) -> Unit
    ) {
        importer.importUri(uri)
        if (currentQuestion.isNotBlank()) {
            onFollowUpQuery(currentQuestion)
        }
    }

    fun reportUnhandledFailure(t: Throwable) {
        try {
            context.filesDir.resolve("rag_crash.log").appendText("${t.stackTraceToString()}\n")
        } catch (_: Throwable) {
        }
    }
}
