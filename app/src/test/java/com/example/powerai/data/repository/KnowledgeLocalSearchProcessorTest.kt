package com.example.powerai.data.repository

import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.data.repository.KnowledgeLocalSearchQueryBuilder
import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.core.data.repository.KnowledgeLocalSearchProcessor

class KnowledgeLocalSearchProcessorTest {
    @Test
    fun `processor returns snippet containing query`() {
        val query = "hello"
        val built = KnowledgeLocalSearchQueryBuilder.buildOrNull(query)!!
        val entity = KnowledgeEntity(
            id = 1,
            title = "",
            content = "This is a hello world example.",
            source = "",
        )
        val list = listOf(entity)
        val items = KnowledgeLocalSearchProcessor.postProcess(list, built) { e ->
            KnowledgeItem(
                id = e.id,
                title = e.title,
                content = e.content,
                source = e.source,
                category = e.category,
                keywords = emptyList(),
                pageNumber = e.pageNumber,
                hitBlockIndex = null,
                hitBlockId = null
            )
        }
        assertEquals(1, items.size)
        assertTrue(items[0].content.contains("hello", ignoreCase = true))
    }

    @Test
    fun `empty built query yields empty result`() {
        val built = KnowledgeLocalSearchQueryBuilder.buildOrNull("   ")
        assertNull(built)
    }
}
