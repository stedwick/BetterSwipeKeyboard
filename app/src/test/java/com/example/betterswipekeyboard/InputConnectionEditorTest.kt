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
}
