package com.example.powerai.core.data.importer

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class ImportUtilsTest {
    private val abc = "abc".toByteArray(Charsets.UTF_8)

    /** FIPS 180-2 standard test vector: SHA-256("abc"). */
    private val abcSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `sha256Hex of raw bytes matches the standard abc vector`() {
        assertEquals(abcSha256, ImportUtils.sha256Hex(abc))
    }

    @Test
    fun `sha256Hex of a string matches the standard abc vector`() {
        assertEquals(abcSha256, ImportUtils.sha256Hex("abc"))
    }

    @Test
    fun `streaming sha256Hex matches the standard abc vector`() {
        assertEquals(abcSha256, ImportUtils.sha256Hex(ByteArrayInputStream(abc)))
    }

    @Test
    fun `hex encodes an already-computed digest without hashing it again`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(abc)
        assertEquals(abcSha256, ImportUtils.hex(digest))
        // Re-hashing the digest would be the double-hash bug; the helpers must differ here.
        assertNotEquals(ImportUtils.hex(digest), ImportUtils.sha256Hex(digest))
    }

    @Test
    fun `sha256Hex returns consistent lowercase hex`() {
        val s = "hello"
        val h1 = ImportUtils.sha256Hex(s)
        val h2 = ImportUtils.sha256Hex(s)
        assertEquals(h1, h2)
        assertTrue(h1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `stableId64 positive and deterministic`() {
        val s = "test"
        val id1 = ImportUtils.stableId64(s)
        val id2 = ImportUtils.stableId64(s)
        assertEquals(id1, id2)
        assertTrue(id1 > 0)
    }
}
