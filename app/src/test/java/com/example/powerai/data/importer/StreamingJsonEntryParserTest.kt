package com.example.powerai.data.importer

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class StreamingJsonEntryParserTest {
    @Test
    fun `fillBuilder returns false for duplicates`() {
        val seen = mutableSetOf<Long>()
        val obj = JsonParser.parseString("{\"entryId\":\"a\"}").asJsonObject
        val builder1 = EntityBuilder()
        val first = StreamingJsonEntryParser.fillBuilder(obj, builder1, seen, JsonResourceParser.FileMetadata("", ""))
        assertTrue(first)
        // second time should be skipped due to existing id
        val builder2 = EntityBuilder()
        val added = StreamingJsonEntryParser.fillBuilder(obj, builder2, seen, JsonResourceParser.FileMetadata("", ""))
        assertFalse(added)
    }

    @Test
    fun `fillBuilder populates fields correctly`() {
        val seen = mutableSetOf<Long>()
        val obj =
            JsonParser.parseString(
                "{\"entryId\":\"id\",\"unitName\":\"U\",\"jobTitle\":\"J\",\"contentMarkdown\":\"foo\",\"tags\":[\"x\",\"y\"]}",
            ).asJsonObject
        val builder = EntityBuilder()
        val added =
            StreamingJsonEntryParser.fillBuilder(
                obj,
                builder,
                seen,
                JsonResourceParser.FileMetadata(fileName = "f", fileId = "fid"),
            )
        assertTrue(added)
        assertEquals("J", builder.title)
        assertEquals("foo", builder.content)
        assertEquals("x,y", builder.keywordsSerialized)
        assertTrue(seen.isNotEmpty())
    }
}
