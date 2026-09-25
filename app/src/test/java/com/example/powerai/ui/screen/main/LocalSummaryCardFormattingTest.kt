package com.example.powerai.ui.screen.main

import com.example.powerai.ui.screen.hybrid.LocalDiagnosticGateLabelFormatter
import com.example.powerai.ui.screen.hybrid.LocalDiagnosticGateSummary
import com.example.powerai.ui.screen.hybrid.LocalDiscardedDiagnosticHit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSummaryCardFormattingTest {

    @Test
    fun `discarded summary line keeps gate details collapsed by default`() {
        val line = buildDiscardedDiagnosticSummaryLine(
            index = 0,
            hit = sampleDiscardedHit()
        )

        assertTrue(line.contains("drop1：noisy_markers(噪声标记偏强)"))
        assertTrue(line.contains("failed=综合信号(combined_signal)+2"))
        assertFalse(line.contains("gates="))
        assertFalse(line.contains("actual="))
    }

    @Test
    fun `discarded gate detail line expands actual threshold details`() {
        val line = buildDiscardedDiagnosticGateDetailLine(sampleDiscardedHit())

        assertTrue(line.contains("gate详情：综合信号(combined_signal):fail(actual=0.32, expected=0.34)"))
        assertTrue(line.contains("噪声过滤(noise_filter):fail(actual=matched, expected=clean)"))
    }

    @Test
    fun `gate toggle label prefers failed gate count and shows total`() {
        assertTrue(buildDiscardedGateToggleLabel("combined_signal", "2", gateCount = 4, expanded = false).contains("展开 综合信号 等 2 个失败 gate 详情（共 4 个 gate）"))
        assertTrue(buildDiscardedGateToggleLabel("combined_signal", "2", gateCount = 4, expanded = true).contains("收起 综合信号 等 2 个失败 gate 详情（共 4 个 gate）"))
        assertTrue(buildDiscardedGateToggleLabel("", "", gateCount = 3, expanded = false).contains("展开 3 个 gate 详情"))
        assertTrue(buildDiscardedGateToggleLabel("", "0", gateCount = 0, expanded = false).contains("展开 gate 详情"))
    }

    @Test
    fun `gate code label maps known gate codes to chinese labels`() {
        assertTrue(LocalDiagnosticGateLabelFormatter.label("combined_signal").contains("综合信号"))
        assertTrue(LocalDiagnosticGateLabelFormatter.label("anchor_gate").contains("锚点要求"))
        assertTrue(LocalDiagnosticGateLabelFormatter.label("unknown_gate").contains("unknown_gate"))
        assertTrue(LocalDiagnosticGateLabelFormatter.labelWithCode("noise_filter").contains("噪声过滤(noise_filter)"))
    }

    private fun sampleDiscardedHit(): LocalDiscardedDiagnosticHit = LocalDiscardedDiagnosticHit(
        title = "巡视要求",
        source = "附件A",
        reasonCode = "noisy_markers",
        reason = "噪声标记偏强",
        reasonDetailCode = "matched_noisy_terms",
        reasonDetail = "附件, 巡视",
        combinedSignal = "0.32",
        combinedThreshold = "0.34",
        anchorHits = "0",
        anchorRequired = "false",
        primaryFailedGate = "combined_signal",
        failedGateCount = "2",
        gateSummaries = listOf(
            LocalDiagnosticGateSummary(
                code = "combined_signal",
                passed = "false",
                actual = "0.32",
                expected = "0.34"
            ),
            LocalDiagnosticGateSummary(
                code = "noise_filter",
                passed = "false",
                actual = "matched",
                expected = "clean"
            )
        ),
        tokenCoverage = "0.10",
        contextualSignal = "0.22"
    )
}
