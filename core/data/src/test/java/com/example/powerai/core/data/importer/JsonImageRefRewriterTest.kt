package com.example.powerai.core.data.importer

import com.google.gson.Gson
import com.google.gson.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The [JsonImageRefRewriter] backs user-directory imports: it rewrites relative shot references
 * (all image aliases plus `images[]`) in place before the entry is mapped, and lets a rewriter
 * failure propagate so a missing/unsafe asset fails the import instead of being swallowed.
 */
class JsonImageRefRewriterTest {
    private val gson = Gson()

    private fun element(json: String): JsonElement = gson.fromJson(json, JsonElement::class.java)

    @Test
    fun `rewrites every image alias and the images array`() {
        val root =
            element(
                """{"blocks":[{"id":"b1","type":"table","imageUri":"shots/a.png","src":"shots/a.png",""" +
                    """"snapshotUri":"shots/a.png","images":["shots/b.png","shots/c.png"]}]}""",
            )

        val changed = JsonImageRefRewriter.rewrite(root) { raw -> "file:///data/mirror/$raw" }

        assertTrue(changed)
        val json = root.toString()
        assertFalse("no raw relative refs remain", json.contains("\"shots/"))
        assertTrue(json.contains("\"file:///data/mirror/shots/a.png\""))
        assertTrue(json.contains("\"file:///data/mirror/shots/b.png\""))
        assertTrue(json.contains("\"file:///data/mirror/shots/c.png\""))
    }

    @Test
    fun `leaves non-image strings untouched`() {
        val root = element("""{"entryId":"e1","jobTitle":"shots/a.png","contentMarkdown":"plain"}""")
        val changed = JsonImageRefRewriter.rewrite(root) { "X" }
        assertFalse(changed)
        assertEquals("e1", root.asJsonObject.get("entryId").asString)
        assertEquals("shots/a.png", root.asJsonObject.get("jobTitle").asString)
    }

    @Test
    fun `rewriter failure propagates instead of being swallowed`() {
        val root = element("""{"blocks":[{"id":"b1","type":"table","imageUri":"../secret.png"}]}""")
        try {
            JsonImageRefRewriter.rewrite(root) { throw IllegalArgumentException("bad ref") }
            fail("expected rewriter failure to propagate")
        } catch (expected: IllegalArgumentException) {
            assertEquals("bad ref", expected.message)
        }
    }
}
