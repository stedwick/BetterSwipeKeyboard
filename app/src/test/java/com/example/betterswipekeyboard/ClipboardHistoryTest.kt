package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.clipboard.ClipboardHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {

    private var clock = 1_000_000L
    private fun history(maxEntries: Int = 50, maxAgeMillis: Long = 3_600_000L) =
        ClipboardHistory(maxEntries = maxEntries, maxAgeMillis = maxAgeMillis, now = { clock })

    @Test
    fun `adds newest first`() {
        val h = history()
        h.add("first")
        clock += 1000
        h.add("second")
        assertEquals(listOf("second", "first"), h.entries().map { it.text })
    }

    @Test
    fun `rejects blank text`() {
        val h = history()
        assertFalse(h.add(""))
        assertFalse(h.add("   "))
        assertFalse(h.add("\n\t"))
        assertTrue(h.entries().isEmpty())
    }

    @Test
    fun `rejects over-length text`() {
        val h = history()
        assertFalse(h.add("x".repeat(10_001)))
        assertTrue(h.add("x".repeat(10_000)))
        assertEquals(1, h.entries().size)
    }

    @Test
    fun `re-adding a text dedups and moves it to the top with fresh timestamp`() {
        val h = history()
        h.add("a")
        clock += 1000
        h.add("b")
        clock += 1000
        h.add("a")
        val entries = h.entries()
        assertEquals(listOf("a", "b"), entries.map { it.text })
        assertEquals(clock, entries.first().copiedAtMillis)
    }

    @Test
    fun `dedup is case sensitive`() {
        // Exact-match semantics: "Hello" and "hello" are two clips the user
        // deliberately copied. Pinned so nobody case-folds this later.
        val h = history()
        h.add("Hello")
        clock += 1000
        h.add("hello")
        assertEquals(listOf("hello", "Hello"), h.entries().map { it.text })
    }

    @Test
    fun `cap evicts the oldest entry`() {
        val h = history(maxEntries = 50)
        repeat(50) { h.add("clip$it") }
        clock += 1000
        h.add("clip50")
        val entries = h.entries()
        assertEquals(50, entries.size)
        assertEquals("clip50", entries.first().text)
        assertEquals("clip1", entries.last().text)
    }

    @Test
    fun `entries older than max age expire`() {
        val h = history(maxAgeMillis = 60_000L)
        h.add("old")
        clock += 30_000
        h.add("new")
        clock += 31_000 // "old" is now 61s old
        assertEquals(listOf("new"), h.entries().map { it.text })
    }

    @Test
    fun `re-copying an expired text reappears as fresh`() {
        val h = history(maxAgeMillis = 60_000L)
        h.add("a")
        clock += 61_000
        assertTrue(h.entries().isEmpty())
        h.add("a")
        assertEquals(listOf("a"), h.entries().map { it.text })
    }

    @Test
    fun `remove deletes exactly the matching entry`() {
        val h = history()
        h.add("a")
        h.add("b")
        h.add("c")
        h.remove("b")
        assertEquals(listOf("c", "a"), h.entries().map { it.text })
        h.remove("nonexistent")
        assertEquals(listOf("c", "a"), h.entries().map { it.text })
    }
}
