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
}
