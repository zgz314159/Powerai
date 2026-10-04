package com.example.powerai.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-allowed relative resource paths only: absolute paths, backslashes and `..` traversal
 * are rejected before anything is copied, so a hand-crafted KB cannot escape the picked directory
 * or the app-private mirror.
 */
class KbResourcePathTest {
    @Test
    fun `accepts plain relative resource paths`() {
        assertEquals(KbResourcePath.Result.Valid("shots/p29_tbl1.png"), KbResourcePath.validate("shots/p29_tbl1.png"))
        assertEquals(KbResourcePath.Result.Valid("img_abc.jpeg"), KbResourcePath.validate("img_abc.jpeg"))
        assertEquals(KbResourcePath.Result.Valid("a/b/c.png"), KbResourcePath.validate("./a//b/c.png"))
    }

    @Test
    fun `rejects traversal and absolute paths`() {
        assertTrue(KbResourcePath.validate("shots/../../secret.png") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("../secret.png") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("/etc/passwd") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("file:///android_asset/x.png") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("C:/Windows/x.png") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("shots\\x.png") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("   ") is KbResourcePath.Result.Invalid)
        assertTrue(KbResourcePath.validate("") is KbResourcePath.Result.Invalid)
    }
}
