package com.example.powerai.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDetailRouteTest {

    @Test
    fun `detail route includes block id parameter`() {
        val route = Screen.Detail.createRoute(
            id = 12L,
            encodedQuery = "abc",
            blockIndex = 3,
            blockId = "table/row?cell=1 2"
        )

        assertTrue(route.contains("detail/12?q=abc&blockIndex=3&blockId="))
        // createRoute currently appends blockId without re-encoding;
        // callers are expected to pass an already-encoded value.
        assertTrue(route.endsWith("blockId=table/row?cell=1 2"))
    }
}