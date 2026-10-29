package com.example.powerai.feature.searchchat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartQueryIntentTest {
    @Test
    fun `bare entity is ambiguous`() {
        assertTrue(SmartQueryIntent.isAmbiguousOrTooShort("变压器"))
        assertTrue(SmartQueryIntent.isAmbiguousOrTooShort("验电"))
        assertTrue(SmartQueryIntent.clarificationMessage("变压器").contains("例如"))
    }

    @Test
    fun `aspect or how-to questions are clear`() {
        assertFalse(SmartQueryIntent.isAmbiguousOrTooShort("变压器绝缘电阻多大"))
        assertFalse(SmartQueryIntent.isAmbiguousOrTooShort("接地电阻"))
        assertFalse(SmartQueryIntent.isAmbiguousOrTooShort("10KV如何验电"))
        assertFalse(SmartQueryIntent.isAmbiguousOrTooShort("10KV最小安全距离"))
    }
}
