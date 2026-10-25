package com.example.powerai.ui.screen.detail

import android.net.Uri

internal object AiTextDetailHighlight {
    fun fromEncodedHighlight(highlightEncoded: String): String = Uri.decode(highlightEncoded).trim()
}
