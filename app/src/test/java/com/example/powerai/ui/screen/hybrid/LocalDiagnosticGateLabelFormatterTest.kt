package com.example.powerai.ui.screen.hybrid

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDiagnosticGateLabelFormatterTest {

    @Test
    fun `known gate codes map to localized labels`() {
        assertEquals("综合信号", LocalDiagnosticGateLabelFormatter.label("combined_signal"))
        assertEquals("锚点要求", LocalDiagnosticGateLabelFormatter.label("anchor_gate"))
        assertEquals("定义证据", LocalDiagnosticGateLabelFormatter.label("definition_evidence"))
        assertEquals("噪声过滤", LocalDiagnosticGateLabelFormatter.label("noise_filter"))
    }

    @Test
    fun `unknown and blank gate codes keep fallback behavior`() {
        assertEquals("future_gate", LocalDiagnosticGateLabelFormatter.label("future_gate"))
        assertEquals("future_gate", LocalDiagnosticGateLabelFormatter.labelWithCode("future_gate"))
        assertEquals("", LocalDiagnosticGateLabelFormatter.labelWithCode(""))
    }
}