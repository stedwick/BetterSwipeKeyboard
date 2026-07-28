package com.example.betterswipekeyboard.proofread

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceExtractorTest {

    @Test
    fun `no boundary returns whole text`() {
        assertEquals("hello world", SentenceExtractor.currentSentence("hello world"))
    }

    @Test
    fun `returns fragment after last period`() {
        assertEquals(" two", SentenceExtractor.currentSentence("one. two"))
    }

    @Test
    fun `returns fragment after last of mixed boundaries`() {
        assertEquals(" three", SentenceExtractor.currentSentence("one! two? three"))
    }

    @Test
    fun `newline is a boundary`() {
        assertEquals("next line", SentenceExtractor.currentSentence("first\nnext line"))
    }

    @Test
    fun `empty after boundary returns empty`() {
        assertEquals("", SentenceExtractor.currentSentence("sentence over. "))
    }

    @Test
    fun `empty input returns empty`() {
        assertEquals("", SentenceExtractor.currentSentence(""))
    }

    @Test
    fun `whitespace only returns empty`() {
        assertEquals("", SentenceExtractor.currentSentence("   "))
    }

    @Test
    fun `whitespace is preserved in the fragment`() {
        // Callers replace exactly this span, so padding must stay intact.
        assertEquals(" hello ", SentenceExtractor.currentSentence("hi. hello "))
    }

    @Test
    fun `ellipsis splits at the last dot`() {
        assertEquals(" what", SentenceExtractor.currentSentence("wait... what"))
    }

    @Test
    fun `window of a single sentence equals currentSentence`() {
        val window = SentenceExtractor.currentWindow("hello world")
        assertEquals(SentenceWindow("hello world", hasPreviousSentence = false), window)
    }

    @Test
    fun `window spans the previous sentence and the current fragment`() {
        val window = SentenceExtractor.currentWindow(
            "I went to the store. and bought some ice cream",
        )
        assertEquals(
            SentenceWindow(
                "I went to the store. and bought some ice cream",
                hasPreviousSentence = true,
            ),
            window,
        )
    }

    @Test
    fun `window is empty when the cursor follows a final period`() {
        // Deliberate: text the user ended with a boundary is treated as
        // final and never re-proofread.
        assertEquals(
            SentenceWindow("", hasPreviousSentence = false),
            SentenceExtractor.currentWindow("I went to the store. And bought ice cream."),
        )
    }

    @Test
    fun `window drops sentences older than the previous one`() {
        val window = SentenceExtractor.currentWindow("One. Two. three")
        assertEquals(SentenceWindow(" Two. three", hasPreviousSentence = true), window)
    }

    @Test
    fun `window is empty right after a sentence boundary`() {
        // Nothing new to proofread; keeps already-final text from being
        // re-proofread.
        assertEquals(
            SentenceWindow("", hasPreviousSentence = false),
            SentenceExtractor.currentWindow("sentence over. "),
        )
    }

    @Test
    fun `window never crosses a newline`() {
        // sentence1\nsentence2 -> the window is sentence2 only: text before
        // a deliberate paragraph break is neither analyzed nor editable.
        val window = SentenceExtractor.currentWindow("First sentence here.\nsecond fragment")
        assertEquals(SentenceWindow("second fragment", hasPreviousSentence = false), window)
    }

    @Test
    fun `fragment after a newline gets no merge context`() {
        // The same fragment WITHIN a paragraph would merge; across a
        // newline the previous sentence is out of reach by design.
        val window = SentenceExtractor.currentWindow("I went to the store.\nand bought ice cream")
        assertEquals(SentenceWindow("and bought ice cream", hasPreviousSentence = false), window)
    }

    @Test
    fun `same-paragraph continuation still merges across a sentence boundary`() {
        val window = SentenceExtractor.currentWindow(
            "Earlier paragraph.\nI went to the store. and bought some ice cream",
        )
        assertEquals(
            SentenceWindow(
                "I went to the store. and bought some ice cream",
                hasPreviousSentence = true,
            ),
            window,
        )
    }

    @Test
    fun `window span can never contain the newline`() {
        // Multiple paragraphs: only the current line is reachable, so no
        // replacement can delete any newline.
        val window = SentenceExtractor.currentWindow("one\ntwo\nthree frag")
        assertEquals(SentenceWindow("three frag", hasPreviousSentence = false), window)
        assertEquals(false, window.text.contains('\n'))
    }

    @Test
    fun `window does not cross a newline between sentences`() {
        val window = SentenceExtractor.currentWindow("one! two?\nthree")
        assertEquals(SentenceWindow("three", hasPreviousSentence = false), window)
    }

    @Test
    fun `window handles ellipsis in the previous sentence`() {
        val window = SentenceExtractor.currentWindow("wait... what next")
        assertEquals(
            SentenceWindow("wait... what next", hasPreviousSentence = true),
            window,
        )
    }

    @Test
    fun `window caps a very long previous sentence at a word boundary`() {
        val previous = "word ".repeat(100).trimEnd() // 499 chars
        val window = SentenceExtractor.currentWindow("$previous. current fragment")
        assertEquals(true, window.hasPreviousSentence)
        assertEquals(true, window.text.endsWith(". current fragment"))
        // The cap keeps the tail only, and never starts mid-word.
        assertEquals(true, window.text.length <= 250 + ". current fragment".length + 5)
        assertEquals(false, window.text.first() == ' ')
    }

    @Test
    fun `window preserves whitespace for exact replacement`() {
        // Callers replace exactly this span, so padding must stay intact.
        val window = SentenceExtractor.currentWindow("hi. hello ")
        assertEquals(SentenceWindow("hi. hello ", hasPreviousSentence = true), window)
    }
}
