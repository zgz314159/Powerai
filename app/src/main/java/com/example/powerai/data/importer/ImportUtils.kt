package com.example.powerai.data.importer

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Shared helper utilities used by import components.
 */
internal object ImportUtils {
    /**
     * Deterministic 64-bit id derived from SHA-256. Keep it positive and non-zero.
     * Falls back to hashCode if SHA-256 is unavailable.
     */
    fun stableId64(input: String): Long {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val bb = ByteBuffer.wrap(digest)
            var v = bb.long and Long.MAX_VALUE
            if (v == 0L) v = 1L
            v
        } catch (_: Throwable) {
            val v = (input.hashCode().toLong() and Long.MAX_VALUE)
            if (v == 0L) 1L else v
        }
    }

    /**
     * Convenient SHA-256 hex digest for strings.
     */
    fun sha256Hex(input: String): String {
        return try {
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            // fall back to hashCode to avoid returning empty
            val h = input.hashCode()
            String.format("%08x", h)
        }
    }
}
