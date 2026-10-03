package com.example.powerai.core.data.importer

import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Shared helper utilities used by import components.
 */
object ImportUtils {
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

    /** Lowercase hex encoding of raw bytes, e.g. an already-computed `MessageDigest.digest()`. */
    fun hex(bytes: ByteArray): String {
        return try {
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Hex SHA-256 **of** the given raw [bytes] (it hashes them). "" when the digest is
     * unavailable (callers treat it as unknown). To encode an already-computed digest use [hex].
     */
    fun sha256Hex(bytes: ByteArray): String {
        return try {
            hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Streaming hex SHA-256 of an [InputStream] (constant memory).
     *
     * Returns "" when the stream cannot be read or the digest is unavailable. Callers must treat
     * an empty result as "unknown" and never as a match for a stored fingerprint.
     */
    fun sha256Hex(inputStream: InputStream): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
            // digest.digest() is already the SHA-256; encode it, do not hash it again.
            hex(digest.digest())
        } catch (_: Throwable) {
            ""
        }
    }

    private const val HASH_BUFFER_BYTES = 8192
}

/**
 * Forwards reads to [delegate] but ignores [close]. The streaming parser closes its reader when it
 * finishes; keeping the underlying (hashing) stream open lets the caller drain any bytes the parser
 * did not consume, so the fingerprint covers the whole file in a single pass.
 */
class NonClosingInputStream(
    private val delegate: InputStream,
) : InputStream() {
    override fun read(): Int = delegate.read()

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int = delegate.read(b, off, len)

    override fun available(): Int = delegate.available()

    override fun close() {
        // Intentionally no-op; the owner closes the underlying stream.
    }
}
