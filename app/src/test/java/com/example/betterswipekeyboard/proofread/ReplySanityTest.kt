package com.example.betterswipekeyboard.proofread

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReplySanityTest {

    // ---- Refusals and garbage are rejected ---------------------------------

    @Test
    fun `the observed nova refusal is rejected`() {
        // Verbatim from eval tag n10 (mice case): would have been committed.
        val reply = "Sorry, I can't provide a correction for this text as " +
            "it doesn't seem to form a coherent sentence."
        assertNotNull(ReplySanity.rejectionReason(MICE_INPUT, reply))
    }

    @Test
    fun `as-an-ai preamble is rejected`() {
        val reply = "As an AI, I cannot provide a corrected version of this text."
        assertNotNull(ReplySanity.rejectionReason(MICE_INPUT, reply))
    }

    @Test
    fun `cannot-help refusal without apology is rejected`() {
        val reply = "I cannot help with this request."
        assertNotNull(ReplySanity.rejectionReason(MICE_INPUT, reply))
    }

    @Test
    fun `blank reply is rejected`() {
        assertNotNull(ReplySanity.rejectionReason(MICE_INPUT, "   "))
    }

    @Test
    fun `off-text hallucination fails the word-overlap gate`() {
        val reply = "A purple elephant dances gracefully under the moonlight."
        assertNotNull(ReplySanity.rejectionReason(FOX_INPUT, reply))
    }

    @Test
    fun `explaining reply that parrots a few words still fails overlap`() {
        val reply = "Your sentence about the fox seems unclear, so I left it."
        assertNotNull(ReplySanity.rejectionReason(FOX_INPUT, reply))
    }

    // ---- Legit replies pass -------------------------------------------------

    @Test
    fun `a real correction with one replaced word passes`() {
        val input = "my very excellent norbert just sold us nine pizzas"
        val reply = "My very excellent mother just sold us nine pizzas."
        assertNull(ReplySanity.rejectionReason(input, reply))
    }

    @Test
    fun `an identity reply passes`() {
        val input = "The train leaves at eight in the morning."
        assertNull(ReplySanity.rejectionReason(input, input))
    }

    @Test
    fun `a correction containing I can't passes — bare I can't is user text`() {
        val input = "i cant believe we won the game"
        val reply = "I can't believe we won the game."
        assertNull(ReplySanity.rejectionReason(input, reply))
    }

    @Test
    fun `a mid-sentence sorry passes — only a LEADING sorry is a marker`() {
        val input = "tell her sorry i missed the bus"
        val reply = "Tell her sorry I missed the bus."
        assertNull(ReplySanity.rejectionReason(input, reply))
    }

    @Test
    fun `a multi-word correction stays above the overlap threshold`() {
        val input = "bounce notice mice ran part the fox"
        val reply = "Nine nice mice ran past the fox."
        assertNull(ReplySanity.rejectionReason(input, reply))
    }

    @Test
    fun `a short reply whose word is not in the input is rejected`() {
        assertNotNull(ReplySanity.rejectionReason("see you tomorrow", "Certainly."))
    }

    private companion object {
        const val MICE_INPUT = "bounce notice mice ran part the fox"
        const val FOX_INPUT = "the quick brown fix jumps over the lazy dog"
    }
}
