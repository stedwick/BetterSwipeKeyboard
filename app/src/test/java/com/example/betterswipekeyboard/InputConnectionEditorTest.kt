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
}
