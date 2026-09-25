package com.example.powerai.data.importer

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class JsonEntryMapperTest {
    private val gson = Gson()
    private val metadata = JsonResourceParser.FileMetadata(
        fileName = "file.json",
        fileId = "fid",
        entriesCount = 0
    )

    @Test
    fun `basic entry maps correctly`() {
        val entry = JsonResourceParser.JsonEntry(
            entryId = "e1",
            unitName = "U",
            jobTitle = "T",
            contentMarkdown = "hello",
            position = 5,
            tags = listOf("alpha","beta")
        )
        val entity = JsonEntryMapper.toEntity(entry, metadata, gson)
        assertEquals("T", entity.title)
        assertEquals("hello", entity.content)
        assertEquals("fid", entity.source)
        assertTrue(entity.id > 0)
        assertEquals("alpha,beta", entity.keywordsSerialized)
    }

    @Test
    fun `image and bbox are extracted from blocksJson`() {
        val blocks = "{\"src\":\"file:///android_asset/img.png\",\"boundingBox\":{\"x\":1}}"
        val entry = JsonResourceParser.JsonEntry(
            entryId = "e2",
            jobTitle = "J",
            contentMarkdown = "m",
            blocks = gson.fromJson(blocks, com.google.gson.JsonElement::class.java)
        )
        val entity = JsonEntryMapper.toEntity(entry, metadata, gson)
        println("debug imageUris=${entity.imageUris} bbox=${entity.bboxJson}")
        assertNotNull(entity.imageUris)
        assertTrue("unexpected imageUris=${entity.imageUris}", entity.imageUris!!.contains("img.png"))
        assertNotNull(entity.bboxJson)
        assertTrue("unexpected bboxJson=${entity.bboxJson}", entity.bboxJson!!.contains("x"))
    }

    @Test
    fun `markdown fallback image extraction works`() {
        val entry = JsonResourceParser.JsonEntry(
            entryId = "e3",
            jobTitle = "J",
            contentMarkdown = "![](file:///android_asset/foo.png)"
        )
        val entity = JsonEntryMapper.toEntity(entry, metadata, gson)
        assertNotNull(entity.imageUris)
        assertTrue(entity.imageUris!!.contains("foo.png"))
    }
}
