package com.example.powerai.ui.screen.hybrid

internal object LocalDiagnosticGateLabelFormatter {
    fun label(code: String): String = when (code) {
        "combined_signal" -> "综合信号"
        "anchor_gate" -> "锚点要求"
        "definition_evidence" -> "定义证据"
        "noise_filter" -> "噪声过滤"
        else -> code
    }

    fun labelWithCode(code: String): String {
        if (code.isBlank()) return ""
        val localized = label(code)
        return if (localized == code) code else "$localized($code)"
    }
}
