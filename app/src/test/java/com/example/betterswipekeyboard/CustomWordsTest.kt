package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.parseCustomWords
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomWordsTest {

    @Test
    fun `splits on any word break`() {
        assertEquals(
            listOf("kimi", "philip", "naruto", "agents"),
            parseCustomWords("Kimi, philip\tnaruto;AGENTS"),
        )
    }

    @Test
    fun `newlines and mixed punctuation separate words`() {
        assertEquals(
            listOf("alpha", "beta", "gamma"),
            parseCustomWords("alpha\nbeta\r\n, ; gamma"),
        )
    }

    @Test
    fun `apostrophe between letters is intra-word, hyphens break`() {
        // Possessives/contractions are swipeable wordforms (letter-only
        // matching, verbatim commit), so they parse as ONE token.
        assertEquals(listOf("spielberg's"), parseCustomWords("Spielberg's"))
        assertEquals(listOf("don't", "maybe"), parseCustomWords("don't, maybe"))
        // Leading/trailing apostrophes are stripped.
        assertEquals(listOf("hello"), parseCustomWords("'hello'"))
        // Hyphens still break: no hyphenated-wordform mechanism exists.
        assertEquals(listOf("mother", "in", "law"), parseCustomWords("mother-in-law"))
    }

    @Test
    fun `dedups case-insensitively keeping first occurrence order`() {
        assertEquals(listOf("hello", "world"), parseCustomWords("Hello HELLO hello world WORLD"))
    }

    @Test
    fun `digits and symbols only yield nothing`() {
        assertEquals(emptyList<String>(), parseCustomWords("  , ; \n\t 123 !!"))
    }

    @Test
    fun `blank input yields nothing`() {
        assertEquals(emptyList<String>(), parseCustomWords(""))
        assertEquals(emptyList<String>(), parseCustomWords("   \n  "))
    }

    @Test
    fun `non-ascii letters are kept`() {
        // The decoder prunes words with keys it does not have; the parser
        // stays keyboard-agnostic.
        assertEquals(listOf("straße", "東京"), parseCustomWords("straße 東京"))
    }

    @Test
    fun `words longer than the cap are dropped`() {
        val long = "a".repeat(33)
        assertEquals(listOf("ok"), parseCustomWords("$long ok"))
        assertEquals(
            listOf("ab"),
            parseCustomWords("abc ab", maxWordLength = 2),
        )
    }

    @Test
    fun `word count is capped`() {
        // Letter-only tokens: digits are word breaks, so "word1" parses as
        // "word". Base-26 letters give 600 distinct words.
        val words = (0 until 600).map { "${'a' + it / 26}${'a' + it % 26}q" }
        val result = parseCustomWords(words.joinToString(" "))
        assertEquals(500, result.size)
        assertEquals(words.first(), result.first())
        assertEquals(words[499], result.last())
    }
}
