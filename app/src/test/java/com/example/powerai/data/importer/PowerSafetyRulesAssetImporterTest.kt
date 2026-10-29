package com.example.powerai.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSafetyRulesAssetImporterTest {
    @Test
    fun `parseEntities maps key content to package rows`() {
        val json =
            """
            [
              {"key":"10kV验电","content":"10kV验电可使用手持式验电器或验电笔。"},
              {"key":"高压验电","content":"高压验电应有专人监护。"}
            ]
            """.trimIndent().toByteArray(Charsets.UTF_8)

        val entities = PowerSafetyRulesAssetImporter.parseEntities(json)!!
        assertEquals(2, entities.size)
        assertEquals("10kV验电", entities[0].title)
        assertTrue(entities[0].content.contains("手持式验电器"))
        assertEquals(PowerSafetyRulesAssetImporter.DISPLAY_SOURCE, entities[0].source)
        assertEquals(PowerSafetyRulesAssetImporter.packageId, entities[0].packageId)
        assertTrue(entities[0].id > 0L)
        assertTrue(entities[0].contentNormalized.isNotBlank())
    }
}
