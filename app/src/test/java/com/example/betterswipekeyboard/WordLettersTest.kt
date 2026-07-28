package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.swipeLetters
import org.junit.Assert.assertEquals
import org.junit.Test

class WordLettersTest {

    @Test
    fun `apostrophe is stripped`() {
        assertEquals("mothers", swipeLetters("mother's"))
        assertEquals("dont", swipeLetters("don't"))
    }

    @Test
    fun `plain words are unchanged`() {
        assertEquals("swipe", swipeLetters("swipe"))
        assertEquals("my", swipeLetters("my"))
    }

    @Test
    fun `multiple apostrophes are all stripped`() {
        // The generator never admits these, but the helper must stay total.
        assertEquals("rocknroll", swipeLetters("rock'n'roll"))
    }
}
