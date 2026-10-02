package com.example.powerai.data.importer

import com.example.powerai.core.model.TableBlock
import com.example.powerai.ui.blocks.BlocksParser
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Cross-project contract test: the PaddleModels v2 production sample must parse
 * through the real JsonResourceParser -> BlocksParser path into correct TableBlocks.
 */
class PaddleModelsTableRowsContractTest {
    private val gson = Gson()

    @Test
    fun `fixture is the byte-identical PaddleModels production sample`() {
        val bytes = fixtureBytes()
        assertEquals(3082, bytes.size)
        assertEquals(
            "ba5e21e8737daabafc816964c79a3777eda04289782ba650f89766dc9f824b8b",
            sha256Hex(bytes),
        )
    }

    @Test
    fun `fixture parses through JsonResourceParser with schemaVersion 20 and one entry`() {
        val root = JsonParser.parseString(fixtureText())
        val (meta, entries) = JsonResourceParser.parseRoot(root, gson, null, null)
        assertEquals("2.0", meta.schemaVersion)
        assertEquals(1, entries.size)
    }

    @Test
    fun `fixture entry yields two TableBlocks`() {
        val tables = fixtureTables()
        assertEquals(2, tables.size)
        assertNotNull(tables.firstOrNull { it.id == "p1_tbl_struct" })
        assertNotNull(tables.firstOrNull { it.id == "p1_tbl_image" })
    }

    @Test
    fun `structured table keeps canonical 3x2 rows and merged placeholder`() {
        val table = fixtureTables().first { it.id == "p1_tbl_struct" }
        assertEquals(
            listOf(
                listOf("H1", "H2"),
                listOf("merged-span", "b1"),
                listOf("", "b2"),
            ),
            table.rows,
        )
        assertEquals(3, table.rows.size)
        assertEquals(2, table.rows[0].size)
        assertEquals("", table.rows[2][0])
    }

    @Test
    fun `structured table exposes five physical cells with merged span`() {
        val table = fixtureTables().first { it.id == "p1_tbl_struct" }
        val cells = table.cells
        assertNotNull(cells)
        assertEquals(5, cells!!.size)
        val merged = cells.first { it.text == "merged-span" }
        assertEquals(1, merged.row)
        assertEquals(0, merged.col)
        assertEquals(2, merged.rowSpan)
        assertEquals(1, merged.colSpan)
    }

    @Test
    fun `image-only table has empty rows and no cells but keeps imageUri`() {
        val table = fixtureTables().first { it.id == "p1_tbl_image" }
        assertTrue(table.rows.isEmpty())
        assertTrue(table.cells.isNullOrEmpty())
        assertEquals("shots/p1_tbl_image.png", table.imageUri)
    }

    @Test
    fun `fixture contains no table_rows no integer rows and no cols`() {
        val tableRows = mutableListOf<Unit>()
        val intRows = mutableListOf<Unit>()
        val cols = mutableListOf<Unit>()
        scan(JsonParser.parseString(fixtureText()), tableRows, intRows, cols)
        assertEquals(0, tableRows.size)
        assertEquals(0, intRows.size)
        assertEquals(0, cols.size)
    }

    @Test
    fun `legacy integer rows does not shadow array table_rows`() {
        val table =
            parseSingleTable(
                """
                {
                  "type": "table",
                  "rows": 6,
                  "cols": 4,
                  "table_rows": [["h1", "h2"], ["v1", "v2"]]
                }
                """.trimIndent(),
            )
        assertEquals(listOf(listOf("h1", "h2"), listOf("v1", "v2")), table.rows)
    }

    @Test
    fun `canonical empty rows wins over populated table_rows`() {
        val table =
            parseSingleTable(
                """
                {
                  "type": "table",
                  "rows": [],
                  "table_rows": [["h1", "h2"]]
                }
                """.trimIndent(),
            )
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun `cells-only table rebuilds a rectangular grid anchored at row and col`() {
        val table =
            parseSingleTable(
                """
                {
                  "type": "table",
                  "cells": [
                    {"row": 0, "col": 0, "text": "A"},
                    {"row": 0, "col": 2, "text": "C"},
                    {"row": 1, "col": 0, "rowSpan": 2, "text": "M"},
                    {"row": 1, "col": 1, "colSpan": 2, "text": "BC"},
                    {"row": 2, "col": 1, "text": "X"}
                  ]
                }
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                listOf("A", "", "C"),
                listOf("M", "BC", ""),
                listOf("", "X", ""),
            ),
            table.rows,
        )
        assertEquals("", table.rows[1][2])
        assertEquals("", table.rows[2][0])
    }

    @Test
    fun `invalid scalar or object rows degrade safely without throwing`() {
        assertTrue(parseSingleTable("""{"type": "table", "rows": 6}""").rows.isEmpty())
        assertTrue(parseSingleTable("""{"type": "table", "rows": "6"}""").rows.isEmpty())
        assertTrue(parseSingleTable("""{"type": "table", "rows": {"a": 1}}""").rows.isEmpty())
    }

    /**
     * Canonical LF bytes. The sample is committed with LF; a CRLF Windows checkout
     * (or resource filtering) must not change the byte-identity contract.
     */
    private fun fixtureBytes(): ByteArray {
        val raw =
            javaClass.getResourceAsStream(FIXTURE_PATH)?.use { it.readBytes() }
                ?: error("fixture not found on classpath: $FIXTURE_PATH")
        return String(raw, Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
    }

    private fun fixtureText(): String = String(fixtureBytes(), Charsets.UTF_8)

    private fun fixtureTables(): List<TableBlock> {
        val root = JsonParser.parseString(fixtureText())
        val (_, entries) = JsonResourceParser.parseRoot(root, gson, null, null)
        val blocks = BlocksParser.parseBlocks(entries[0].blocks.toString()).orEmpty()
        return blocks.filterIsInstance<TableBlock>()
    }

    private fun parseSingleTable(blockJson: String): TableBlock {
        val blocks = BlocksParser.parseBlocks("""{"blocks": [$blockJson]}""").orEmpty()
        return blocks.filterIsInstance<TableBlock>().single()
    }

    private fun scan(
        element: JsonElement,
        tableRows: MutableList<Unit>,
        intRows: MutableList<Unit>,
        cols: MutableList<Unit>,
    ) {
        when {
            element.isJsonObject -> {
                for ((key, value) in element.asJsonObject.entrySet()) {
                    when (key) {
                        "table_rows" -> tableRows.add(Unit)
                        "cols" -> cols.add(Unit)
                        "rows" -> if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) intRows.add(Unit)
                    }
                    scan(value, tableRows, intRows, cols)
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach { scan(it, tableRows, intRows, cols) }
            else -> Unit
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE_PATH = "/contracts/paddlemodels_v2_table_rows_contract.json"
    }
}
