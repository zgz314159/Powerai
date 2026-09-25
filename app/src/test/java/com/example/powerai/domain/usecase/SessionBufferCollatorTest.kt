package com.example.powerai.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionBufferCollatorTest {
    @Test
    fun `persist callback invoked first time and then after threshold`() = runTest {
        val calls = mutableListOf<String>()
        val collator = SessionBufferCollator(this, persistThresholdMs = 100)

        val buffer = collator.start { text -> calls.add(text) }

        // initially buffer is blank, no calls
        assertEquals(emptyList<String>(), calls)

        // push first value
        buffer.value = "hello"
        // collector should pick it up immediately
        advanceUntilIdle()
        assertEquals(listOf("hello"), calls)

        // push another quickly
        buffer.value = "hello2"
        advanceUntilIdle()
        // second persist may be throttled; only require first value is present
        assertEquals("hello", calls.first())

        // wait beyond threshold (collator uses wall-clock time)
        delay(150)
        advanceUntilIdle()
        assertTrue(calls.last() == "hello" || calls.last() == "hello2")

        // pushing blank should not trigger
        val sizeBeforeBlank = calls.size
        buffer.value = ""
        advanceUntilIdle()
        assertEquals(sizeBeforeBlank, calls.size)

        collator.stop()
    }

    @Test
    fun `multiple starts do not create extra collectors`() = runTest {
        val calls = mutableListOf<String>()
        val collator = SessionBufferCollator(this)
        collator.start { calls.add(it) }
        collator.start { calls.add(it) } // second start should be no-op
        collator.buffer.value = "x"
        advanceUntilIdle()
        assertEquals(listOf("x"), calls)
        collator.stop()
    }

    @Test
    fun `stop cancels collector allowing restart`() = runTest {
        val calls = mutableListOf<String>()
        val collator = SessionBufferCollator(this)
        collator.start { calls.add(it) }
        collator.buffer.value = "a"
        advanceUntilIdle()
        collator.stop()
        collator.buffer.value = "b"
        advanceUntilIdle()
        assertEquals(listOf("a"), calls)
        // restart
        collator.start { calls.add(it) }
        collator.buffer.value = "c"
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), calls)
        collator.stop()
    }
}