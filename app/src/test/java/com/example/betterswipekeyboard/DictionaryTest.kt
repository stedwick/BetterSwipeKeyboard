package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DictionaryTest {

    private val base = Dictionary(
        listOf(
            WordEntry("apple", 10),
            WordEntry("avocado", 200),
            WordEntry("banana", 30),
        ),
    )

    @Test
    fun `custom words appear at top frequency rank`() {
        val merged = base.withCustomWords(listOf("aviate", "borf"))
        assertEquals(WordEntry("aviate", 1), merged.startingWith('a').first { it.word == "aviate" })
        assertEquals(WordEntry("borf", 1), merged.startingWith('b').first { it.word == "borf" })
    }

    @Test
    fun `custom words already in the dictionary are not duplicated`() {
        val merged = base.withCustomWords(listOf("apple"))
        assertEquals(1, merged.startingWith('a').count { it.word == "apple" })
        // The built-in entry keeps its real rank.
        assertEquals(10, merged.startingWith('a').first { it.word == "apple" }.rank)
    }

    @Test
    fun `maxRank is unchanged by custom words`() {
        assertEquals(200, base.withCustomWords(listOf("zzz")).maxRank)
    }

    @Test
    fun `empty custom list returns the same dictionary`() {
        assertSame(base, base.withCustomWords(emptyList()))
    }

    @Test
    fun `all-duplicates custom list returns the same dictionary`() {
        assertSame(base, base.withCustomWords(listOf("apple", "banana")))
    }

    @Test
    fun `empty custom words are ignored`() {
        assertSame(base, base.withCustomWords(listOf("")))
    }
}
