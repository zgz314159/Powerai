package com.example.powerai.feature.searchchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmartAnswerGuardTest {
    @Test
    fun `strips leading lone question mark`() {
        val raw = "?\n10kV验电可使用手持式验电器或验电笔。"
        val cleaned = SmartAnswerGuard.stripMetaReasoningLead(raw)
        assertFalse(cleaned.startsWith("?"))
        assertEquals("10kV验电可使用手持式验电器或验电笔。", cleaned)
    }
}
