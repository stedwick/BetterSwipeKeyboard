package com.example.betterswipekeyboard.proofread

/**
 * Extracts the sentence the cursor is currently in, from the text before the
 * cursor. Pure logic, unit-tested.
 */
object SentenceExtractor {

    /**
     * Returns the raw fragment after the last sentence boundary (`.`, `!`,
     * `?`, newline), exactly as it appears before the cursor — surrounding
     * whitespace preserved so callers can replace precisely this span.
     * Returns "" when nothing meaningful precedes the cursor.
     */
    fun currentSentence(textBeforeCursor: String): String {
        val fragment = textBeforeCursor.substringAfterLast('.')
            .substringAfterLast('!')
            .substringAfterLast('?')
            .substringAfterLast('\n')
        return fragment.takeIf { it.isNotBlank() } ?: ""
    }
}
