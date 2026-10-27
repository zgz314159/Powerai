package com.example.powerai.ui.screen.detail

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient

internal object KnowledgeDetailWebViewDefaults {
    fun applyTableDefaults(webView: WebView) {
        webView.settings.javaScriptEnabled = false
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = WebViewClient()
        webView.setBackgroundColor(Color.TRANSPARENT)
    }
}
