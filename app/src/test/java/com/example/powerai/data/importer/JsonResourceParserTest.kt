package com.example.powerai.data.importer

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class JsonResourceParserTest {
    private val gson = Gson()

    @Test
    fun `parse simple schema returns metadata and entries`() {
        val json = """{
            "fileMetadata": {"fileName":"f","fileId":"id"},
            "entries": [{"entryId":"e1","contentMarkdown":"c"}]
        }""".trimIndent()
        val root = JsonParser.parseString(json)
        val (meta, entries) = JsonResourceParser.parseRoot(root, gson, null, null)
        assertEquals("f", meta.fileName)
        assertEquals("id", meta.fileId)
        assertEquals(1, entries.size)
    }

    @Test
    fun `fallback names used when legacy schema missing`() {
        val json = """{ "entries": [{"entryId":"x"}] }"""
        val root = JsonParser.parseString(json)
        val (meta, entries) = JsonResourceParser.parseRoot(root, gson, "name", "fid")
        assertEquals("name", meta.fileName)
        assertEquals("fid", meta.fileId)
        assertEquals(1, entries.size)
    }

    @Test
    fun `array root parsed correctly`() {
        val json = """[ {"entryId":"a"}, {"entryId":"b"} ]"""
        val root = JsonParser.parseString(json)
        val (meta, entries) = JsonResourceParser.parseRoot(root, gson, "n","i")
        assertEquals(2, entries.size)
        assertEquals("n", meta.fileName)
        assertEquals("i", meta.fileId)
    }
}
