package com.example.betterswipekeyboard.proofread

/**
 * The proofread window: the current fragment plus the previous sentence as
 * context, exactly as they appear before the cursor. The whole window is
 * the editable span — merging a continuation fragment into the previous
 * sentence inherently edits the boundary between them. The window never
 * crosses a newline: a newline is a deliberate user boundary (paragraphs,
 * lists), so neither analysis nor the replacement span may reach before
 * the last one, and continuation merging stays possible only WITHIN a
 * paragraph.
 */
data class SentenceWindow(val text: String, val hasPreviousSentence: Boolean)

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

    /**
     * Returns the current fragment plus the previous sentence (context), as
     * the exact substring of [textBeforeCursor] ending at the cursor, so
     * callers can replace precisely this span. Needed because an auto
     * proofread during a mid-thought pause can terminate a sentence the
     * user later continues ("... the store." + "and bought ice cream.") —
     * without the previous sentence the proofreader cannot merge them.
     *
     * The window NEVER crosses the last newline before the cursor: a
     * newline is a deliberate user boundary (paragraphs, lists), so text
     * before it is neither analyzed nor editable, and the newline itself
     * can never be removed by a replacement. A fragment starting a new
     * paragraph gets no previous-sentence context and therefore no merge.
     *
     * The previous sentence is capped to its last [maxPreviousChars]
     * (starting at a word boundary, so the editable span never begins
     * mid-word). Returns an empty window when the cursor sits right after
     * a sentence boundary — there is nothing new to proofread, and this
     * also keeps already-final text from being re-proofread.
     */
    fun currentWindow(
        textBeforeCursor: String,
        maxPreviousChars: Int = MAX_PREVIOUS_SENTENCE_CHARS,
    ): SentenceWindow {
        val current = currentSentence(textBeforeCursor)
        if (current.isEmpty()) return SentenceWindow("", hasPreviousSentence = false)

        // Everything up to and including the boundary that started the
        // current fragment. Strip that sentence-final punctuation (and any
        // ellipsis/whitespace around it) so the previous sentence itself
        // becomes visible to currentSentence — otherwise the trailing
        // boundary always yields an empty fragment.
        val rest = textBeforeCursor.dropLast(current.length).trimEnd()
        val beforeBoundary = rest.dropLastWhile { it == '.' || it == '!' || it == '?' }.trimEnd()
        if (beforeBoundary.isBlank()) {
            return SentenceWindow(current, hasPreviousSentence = false)
        }

        val previous = currentSentence(beforeBoundary).let { sentence ->
            if (sentence.length <= maxPreviousChars) {
                sentence
            } else {
                sentence.takeLast(maxPreviousChars)
                    .substringAfter(' ', missingDelimiterValue = sentence)
            }
        }
        val start = beforeBoundary.length - previous.length
        // A newline is a deliberate user boundary (paragraphs, lists): clamp
        // the window start past the last newline so neither analysis nor the
        // replacement span can reach before it — the newline itself can
        // never be deleted by a replacement, and merging stays possible only
        // within a paragraph. When the clamp swallows the previous sentence
        // entirely, the window is the current fragment alone.
        val afterLastNewline = textBeforeCursor.lastIndexOf('\n') + 1
        val clampedStart = maxOf(start, afterLastNewline)
        val fragmentStart = textBeforeCursor.length - current.length
        return SentenceWindow(
            textBeforeCursor.substring(clampedStart),
            hasPreviousSentence = clampedStart < fragmentStart,
        )
    }

    /** Cap on how much of the previous sentence goes into the prompt. */
    private const val MAX_PREVIOUS_SENTENCE_CHARS = 250
}
