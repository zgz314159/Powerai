package com.example.powerai.core.model.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization tests for the single surviving JsonUtils implementation.
 *
 * They pin the current escape/unescape semantics (only backslash, quote, LF
 * and CR are escaped; replacements run backslash-first) so that consolidating
 * call sites cannot silently change the JSON wire format.
 */
class JsonUtilsTest {
    @Test
    fun `escapeJson escapes backslash quote newline and carriage return`() {
        assertEquals("\\\\", JsonUtils.escapeJson("\\"))
        assertEquals("\\\"", JsonUtils.escapeJson("\""))
        assertEquals("\\n", JsonUtils.escapeJson("\n"))
        assertEquals("\\r", JsonUtils.escapeJson("\r"))
    }

    @Test
    fun `escapeJson leaves tabs unicode and other control chars untouched`() {
        assertEquals("a\tb", JsonUtils.escapeJson("a\tb"))
        assertEquals("héllo 你好", JsonUtils.escapeJson("héllo 你好"))
        assertEquals("a/b<c>&d", JsonUtils.escapeJson("a/b<c>&d"))
    }

    @Test
    fun `escapeJson doubles backslashes before other replacements run`() {
        // Input is a backslash followed by the letter n; the backslash
        // replacement runs first, so the result is two backslashes plus n.
        assertEquals("\\\\n", JsonUtils.escapeJson("\\n"))
    }

    @Test
    fun `unescapeJson decodes escaped newline quote and backslash`() {
        assertEquals("\n", JsonUtils.unescapeJson("\\n"))
        assertEquals("\"", JsonUtils.unescapeJson("\\\""))
        assertEquals("\\", JsonUtils.unescapeJson("\\\\"))
    }

    @Test
    fun `unescapeJson decodes escaped backslash plus n as backslash plus newline`() {
        // Historical order-dependent behaviour: the input is an escaped
        // backslash followed by a literal n, and the first replacement turns
        // the trailing backslash+n into a newline instead of decoding
        // backslash+n as text.
        assertEquals("\\\n", JsonUtils.unescapeJson("\\\\n"))
    }

    @Test
    fun `escape then unescape round trips typical user text`() {
        val text = "line1\nline2\t\"quoted\" \\ end"
        assertEquals(text, JsonUtils.unescapeJson(JsonUtils.escapeJson(text)))
    }

    @Test
    fun `unescapeJson does not decode carriage return escapes`() {
        // Historical asymmetry: escapeJson escapes CR, unescapeJson has no
        // \r rule, so an escaped CR stays a two-character sequence.
        assertEquals("\\r", JsonUtils.escapeJson("\r"))
        assertEquals("\\r", JsonUtils.unescapeJson("\\r"))
    }
}
