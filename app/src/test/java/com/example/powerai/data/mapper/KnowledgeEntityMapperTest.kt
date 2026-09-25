package com.example.powerai.data.mapper

import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.core.data.mapper.KnowledgeEntityMapper

class KnowledgeEntityMapperTest {
    @Test
    fun `toEntity and toItem round trip preserves fields`() {
        val item = KnowledgeItem(
            id = 123L,
            title = "Title",
            content = "Some content",
            source = "src",
            pageNumber = 5,
            category = "cat",
            keywords = listOf("a", "b")
        )
        val entity = KnowledgeEntityMapper.toEntity(item)
        // id not set by mapper; ensure default zero
        assertEquals(0L, entity.id)
        assertEquals(item.title, entity.title)
        assertEquals(item.content, entity.content)
        assertEquals(item.source, entity.source)
        assertEquals(item.category, entity.category)
        assertTrue(entity.keywordsSerialized.contains("a"))

        val item2 = KnowledgeEntityMapper.toItem(entity.copy(id = 123L))
        assertEquals(123L, item2.id)
        assertEquals(item.title, item2.title)
        assertEquals(item.content, item2.content)
        assertEquals(item.source, item2.source)
        assertEquals(item.category, item2.category)
        assertEquals(item.keywords, item2.keywords)
    }

    @Test
    fun `toItem handles empty keywordsSerialized`() {
        val entity = KnowledgeEntity(id = 0L, title = "", content = "", source = "", keywordsSerialized = "")
        val item = KnowledgeEntityMapper.toItem(entity)
        assertTrue(item.keywords.isEmpty())
    }
}