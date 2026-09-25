package com.example.powerai.domain.llm

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.example.powerai.engine.ai.GemmaResponseSanitizer

class GemmaResponseSanitizerTest {
    @Test
    fun `isRepetitive flags obvious repeats`() {
        // the heuristic requires more than 3 matches; craft string with four groups
        assertTrue(GemmaResponseSanitizer.isRepetitive("1111 2222 3333 4444"))
        assertTrue(GemmaResponseSanitizer.isRepetitive("21kV21kV21kV21kV"))
        assertFalse(GemmaResponseSanitizer.isRepetitive("normal text"))
    }

    @Test
    fun `cleanFinalText truncates long and repetitive output`() {
        val long = "a".repeat(4000)
        val (shortened, truncated) = GemmaResponseSanitizer.cleanFinalText(long)
        // ensure function returns without throwing and output isn't longer than input
        assertTrue(shortened.length <= long.length)

        val repeat = "21kV".repeat(10)
        val (r2, t2) = GemmaResponseSanitizer.cleanFinalText(repeat)
        assertTrue(t2)
        assertTrue(r2.contains("系统已截断"))
    }

    @Test
    fun `tokenCollapseDetected returns true when pattern present`() {
        assertFalse(GemmaResponseSanitizer.tokenCollapseDetected("", null))
        assertTrue(GemmaResponseSanitizer.tokenCollapseDetected("", "kVkVkVkV"))
        assertTrue(GemmaResponseSanitizer.tokenCollapseDetected("", "abcdeabcdeabcde"))
    }

    @Test
    fun `writeEmergencyStop writes marker when context available`() {
        val ctx = mock(Context::class.java)
        // simulate filesDir by returning temp dir
        val tmp = java.io.File(System.getProperty("java.io.tmpdir"))
        `when`(ctx.filesDir).thenReturn(tmp)

        GemmaResponseSanitizer.writeEmergencyStop(ctx)
        val outFile = java.io.File(tmp, "last_res.txt")
        // marker should be appended even if file empty
        assertTrue(outFile.exists())
        val content = outFile.readText()
        assertTrue(content.contains("EMERGENCY_SHUTDOWN_TRIGGERED"))
    }

    @Test
    fun `writeEmergencyStop with null context leaves working directory last_res untouched`() {
        val cwdLastRes = java.io.File("last_res.txt")
        val existedBefore = cwdLastRes.exists()
        val shaBefore = if (existedBefore) sha256(cwdLastRes.readBytes()) else null

        GemmaResponseSanitizer.writeEmergencyStop(null)

        assertEquals(existedBefore, cwdLastRes.exists())
        val shaAfter = if (existedBefore) sha256(cwdLastRes.readBytes()) else null
        assertEquals(shaBefore, shaAfter)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
