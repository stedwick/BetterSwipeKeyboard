package com.example.betterswipekeyboard.proofread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipedWordLogTest {

    private fun entry(word: String) = SwipedWordLog.Entry(word, "$word-path")

    @Test
    fun `entries reconcile in commit order with match positions`() {
        val log = SwipedWordLog()
        log.record("dog", "d·o·g")
        log.record("ran", "r·a·n")
        val matches = log.reconcile("the dog ran over")
        assertEquals(2, matches.size)
        assertEquals("dog", matches[0].entry.word)
        assertEquals(4, matches[0].startIndex)
        assertEquals(7, matches[0].endIndex)
        assertEquals("ran", matches[1].entry.word)
        assertEquals(8, matches[1].startIndex)
    }

    @Test
    fun `duplicate words map to duplicate occurrences in order`() {
        val log = SwipedWordLog()
        log.record("the", "t1")
        log.record("dog", "d")
        log.record("the", "t2")
        val matches = log.reconcile("the dog the cat")
        assertEquals(3, matches.size)
        assertEquals(0, matches[0].startIndex)
        assertEquals(8, matches[2].startIndex)
        assertEquals("t1", matches[0].entry.letters)
        assertEquals("t2", matches[2].entry.letters)
    }

    @Test
    fun `a dropped entry consumes nothing and later entries still match`() {
        // dog was word-deleted (retry); cat still found afterwards.
        val matches = SwipedWordLog.reconcile(listOf(entry("dog"), entry("cat")), "a cat")
        assertEquals(1, matches.size)
        assertEquals("cat", matches[0].entry.word)
    }

    @Test
    fun `partial edit drops the entry`() {
        assertTrue(SwipedWordLog.reconcile(listOf(entry("hello")), "hell").isEmpty())
        assertTrue(SwipedWordLog.reconcile(listOf(entry("hello")), "helloo").isEmpty())
    }

    @Test
    fun `whole-word matching rejects substrings on both sides`() {
        assertTrue(SwipedWordLog.reconcile(listOf(entry("cat")), "cats").isEmpty())
        assertTrue(SwipedWordLog.reconcile(listOf(entry("cat")), "scat").isEmpty())
        assertTrue(SwipedWordLog.reconcile(listOf(entry("cat")), "concatenate").isEmpty())
        // But punctuation and digits-adjacent-punctuation boundaries are fine.
        assertEquals(1, SwipedWordLog.reconcile(listOf(entry("cat")), "the cat!").size)
        assertEquals(1, SwipedWordLog.reconcile(listOf(entry("cat")), "(cat)").size)
        // Digits are word characters: cat1 is not cat.
        assertTrue(SwipedWordLog.reconcile(listOf(entry("cat")), "cat1").isEmpty())
    }

    @Test
    fun `apostrophe does not close a word`() {
        // mother vs mother's: the possessive is a different (edited) word.
        assertTrue(SwipedWordLog.reconcile(listOf(entry("mother")), "mother's").isEmpty())
        // But an apostrophe word itself matches verbatim.
        assertEquals(1, SwipedWordLog.reconcile(listOf(entry("mother's")), "my mother's car").size)
    }

    @Test
    fun `matching is case-sensitive`() {
        assertTrue(SwipedWordLog.reconcile(listOf(entry("His")), "his office").isEmpty())
        assertEquals(1, SwipedWordLog.reconcile(listOf(entry("His")), "His office").size)
    }

    @Test
    fun `same word typed twice matches the first occurrence`() {
        // Deliberate, documented: we cannot tell which occurrence was
        // swiped, so commit order wins (oldest entry, earliest text).
        val matches = SwipedWordLog.reconcile(listOf(entry("dog")), "dog and dog")
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].startIndex)
    }

    @Test
    fun `inserted text between entries does not invalidate them`() {
        val matches = SwipedWordLog.reconcile(listOf(entry("hello"), entry("world")), "hello brave world")
        assertEquals(2, matches.size)
    }

    @Test
    fun `empty text and empty log yield no matches`() {
        assertTrue(SwipedWordLog.reconcile(listOf(entry("dog")), "").isEmpty())
        assertTrue(SwipedWordLog().reconcile("anything").isEmpty())
    }

    @Test
    fun `record ignores blank words and blank letters`() {
        val log = SwipedWordLog()
        log.record("", "x")
        log.record("dog", "")
        log.record("  ", "x")
        assertTrue(log.reconcile("dog").isEmpty())
    }

    @Test
    fun `cap evicts the oldest entries`() {
        val log = SwipedWordLog(cap = 3)
        log.record("one", "1")
        log.record("two", "2")
        log.record("three", "3")
        log.record("four", "4")
        val matches = log.reconcile("one two three four")
        // 'one' was evicted; the rest survive.
        assertEquals(listOf("two", "three", "four"), matches.map { it.entry.word })
    }

    @Test
    fun `alternates ride along from record through reconcile`() {
        val log = SwipedWordLog()
        log.record("east", "w·a·s·r·e", listOf("wars", "eats"))
        val matches = log.reconcile("star east")
        assertEquals(1, matches.size)
        assertEquals(listOf("wars", "eats"), matches[0].entry.alternates)
    }

    @Test
    fun `record without alternates defaults to an empty list`() {
        val log = SwipedWordLog()
        log.record("dog", "d·o·g")
        assertEquals(emptyList<String>(), log.reconcile("dog")[0].entry.alternates)
    }

    @Test
    fun `reconciliation matches on the word only - alternates play no part`() {
        // The runner-ups annotate; they never widen what counts as a match.
        val entries = listOf(SwipedWordLog.Entry("east", "p", listOf("wars")))
        assertEquals(1, SwipedWordLog.reconcile(entries, "east").size)
        assertTrue(SwipedWordLog.reconcile(entries, "wars").isEmpty())
    }
}
