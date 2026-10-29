package com.example.powerai.feature.searchchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartQueryRouterTest {
    @Test
    fun `classifies temporal date questions`() {
        assertEquals(SmartQueryKind.TEMPORAL, SmartQueryRouter.classify("今天几号？"))
        assertEquals(SmartQueryKind.TEMPORAL, SmartQueryRouter.classify("最新新闻"))
    }

    @Test
    fun `classifies domain power questions`() {
        assertEquals(SmartQueryKind.DOMAIN, SmartQueryRouter.classify("验电应使用什么验电器"))
        assertEquals(SmartQueryKind.DOMAIN, SmartQueryRouter.classify("接地电阻要求"))
    }

    @Test
    fun `classifies chitchat`() {
        assertEquals(SmartQueryKind.CHITCHAT, SmartQueryRouter.classify("你好吗"))
    }

    @Test
    fun `web fetch rules`() {
        assertFalse(SmartQueryRouter.shouldFetchWeb(SmartQueryKind.DOMAIN, webSearchEnabled = false, localHitCount = 0))
        assertTrue(SmartQueryRouter.shouldFetchWeb(SmartQueryKind.DOMAIN, webSearchEnabled = true, localHitCount = 0))
        assertFalse(SmartQueryRouter.shouldFetchWeb(SmartQueryKind.DOMAIN, webSearchEnabled = true, localHitCount = 2))
        assertTrue(SmartQueryRouter.shouldFetchWeb(SmartQueryKind.TEMPORAL, webSearchEnabled = true, localHitCount = 0))
        assertTrue(SmartQueryRouter.shouldFetchWeb(SmartQueryKind.TEMPORAL, webSearchEnabled = true, localHitCount = 1))
        assertFalse(SmartQueryRouter.shouldFetchWeb(SmartQueryKind.TEMPORAL, webSearchEnabled = false, localHitCount = 0))
    }

    @Test
    fun `factual numeric ask detected`() {
        assertTrue(SmartQueryRouter.isFactualNumericAsk("变压器绝缘电阻多大"))
        assertFalse(SmartQueryRouter.isFactualNumericAsk("10KV如何验电"))
    }

    @Test
    fun `factual ask with empty local still fetches web when enabled`() {
        assertTrue(
            SmartQueryRouter.shouldFetchWeb(
                SmartQueryKind.DOMAIN,
                webSearchEnabled = true,
                localHitCount = 0,
                question = "变压器绝缘电阻多大",
            ),
        )
    }
}
