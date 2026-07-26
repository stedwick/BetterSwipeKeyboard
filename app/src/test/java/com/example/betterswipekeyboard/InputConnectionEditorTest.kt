package com.example.betterswipekeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class InputConnectionEditorTest {

    @Test
    fun `no leading space at the very start of the field`() {
        assertEquals("word", InputConnectionEditor.withLeadingSpace(null, "word"))
        assertEquals("word", InputConnectionEditor.withLeadingSpace("", "word"))
    }

    @Test
    fun `no leading space after existing whitespace`() {
        assertEquals("word", InputConnectionEditor.withLeadingSpace(" ", "word"))
        assertEquals("word", InputConnectionEditor.withLeadingSpace("\n", "word"))
    }

    @Test
    fun `leading space after a letter`() {
        assertEquals(" word", InputConnectionEditor.withLeadingSpace("a", "word"))
    }

    @Test
    fun `leading space after punctuation`() {
        assertEquals(" word", InputConnectionEditor.withLeadingSpace(".", "word"))
    }

    @Test
    fun `tapped letter after swipe needs a space`() {
        assertEquals(true, InputConnectionEditor.needsSpaceAfterSwipe("e", "x"))
    }

    @Test
    fun `no space for tapped punctuation after swipe`() {
        assertEquals(false, InputConnectionEditor.needsSpaceAfterSwipe("e", ","))
        assertEquals(false, InputConnectionEditor.needsSpaceAfterSwipe("e", "."))
    }

    @Test
    fun `no space for the space bar after swipe`() {
        assertEquals(false, InputConnectionEditor.needsSpaceAfterSwipe("e", " "))
    }

    @Test
    fun `no space after swipe at field start or after whitespace`() {
        assertEquals(false, InputConnectionEditor.needsSpaceAfterSwipe(null, "x"))
        assertEquals(false, InputConnectionEditor.needsSpaceAfterSwipe("", "x"))
        assertEquals(false, InputConnectionEditor.needsSpaceAfterSwipe(" ", "x"))
    }

    @Test
    fun `grapheme length falls back to one with no text`() {
        assertEquals(1, InputConnectionEditor.precedingGraphemeLength(null))
        assertEquals(1, InputConnectionEditor.precedingGraphemeLength(""))
    }

    @Test
    fun `grapheme length of plain ascii is one`() {
        assertEquals(1, InputConnectionEditor.precedingGraphemeLength("a"))
        assertEquals(1, InputConnectionEditor.precedingGraphemeLength("hello"))
    }

    @Test
    fun `grapheme length of surrogate-pair emoji is two`() {
        assertEquals(2, InputConnectionEditor.precedingGraphemeLength("😀"))
        assertEquals(2, InputConnectionEditor.precedingGraphemeLength("text😀"))
    }

    @Test
    fun `grapheme length of regional-indicator flag is four`() {
        assertEquals(4, InputConnectionEditor.precedingGraphemeLength("🇩🇪"))
    }

    @Test
    fun `grapheme length of letter plus combining mark deletes both`() {
        assertEquals(2, InputConnectionEditor.precedingGraphemeLength("é"))
        assertEquals(1, InputConnectionEditor.precedingGraphemeLength("éx"))
    }

    @Test
    fun `grapheme length of zwj family emoji deletes whole sequence`() {
        // 👨 ZWJ 👩 ZWJ 👧 = 2+1+2+1+2 = 8 UTF-16 code units. The JVM
        // BreakIterator reports this as one cluster; if a future JDK splits
        // it, one backspace removing one sub-emoji is accepted.
        assertEquals(8, InputConnectionEditor.precedingGraphemeLength("👨‍👩‍👧"))
    }
}
