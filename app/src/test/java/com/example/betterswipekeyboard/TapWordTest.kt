package com.example.betterswipekeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapWordTest {

    @Test
    fun `prefix is the trailing word up to the cursor`() {
        assertEquals("hello", currentWordPrefix("hello"))
        assertEquals("hel", currentWordPrefix("say hel"))
    }

    @Test
    fun `prefix is empty right after a boundary character`() {
        assertEquals("", currentWordPrefix("hello "))
        assertEquals("", currentWordPrefix("hello,"))
        assertEquals("", currentWordPrefix("abc1"))
        assertEquals("", currentWordPrefix("hello\n"))
        assertEquals("", currentWordPrefix(""))
    }

    @Test
    fun `apostrophe is kept inside the word`() {
        assertEquals("don'", currentWordPrefix("don'"))
        assertEquals("don't", currentWordPrefix("don't"))
    }

    @Test
    fun `prefix keeps unicode letters and verbatim caps`() {
        assertEquals("Hel", currentWordPrefix("Hel"))
        assertEquals("über", currentWordPrefix("über"))
    }

    @Test
    fun `word before the boundary run is the just-ended word`() {
        assertEquals("hello", tappedWordBeforeBoundary("hello "))
        assertEquals("hello", tappedWordBeforeBoundary("hello, "))
        assertEquals("hello", tappedWordBeforeBoundary("hello."))
        assertEquals("Hello", tappedWordBeforeBoundary("Hello, "))
    }

    @Test
    fun `digits end a word like punctuation`() {
        assertEquals("", currentWordPrefix("abc123"))
        // The boundary run is "123 " — the word behind it still shows.
        assertEquals("abc", tappedWordBeforeBoundary("abc123 "))
    }

    @Test
    fun `newline is a hard boundary that is never crossed`() {
        assertNull(tappedWordBeforeBoundary("hello\n"))
        assertEquals("hello", tappedWordBeforeBoundary("a\nhello "))
        assertEquals("world", tappedWordBeforeBoundary("hello\nworld "))
    }

    @Test
    fun `no word behind the boundary run means null`() {
        assertNull(tappedWordBeforeBoundary(""))
        assertNull(tappedWordBeforeBoundary(" "))
        assertNull(tappedWordBeforeBoundary(", "))
    }
}
