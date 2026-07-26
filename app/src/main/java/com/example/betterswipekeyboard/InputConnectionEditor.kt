package com.example.betterswipekeyboard

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * The only class that talks to the text field. It resolves the
 * [InputConnection] lazily because the connection changes every time focus
 * moves between fields.
 */
class InputConnectionEditor(
    private val connectionProvider: () -> InputConnection?,
) {
    fun commitText(text: String) {
        connectionProvider()?.commitText(text, 1)
    }

    /**
     * Commits a swiped word, inserting a leading space when the text before
     * the cursor doesn't already end in whitespace (and never at the very
     * start of the field).
     */
    fun commitWord(word: String) {
        val before = textBeforeCursor(maxChars = 1)
        commitText(withLeadingSpace(before, word))
    }

    fun backspace() {
        val ic = connectionProvider() ?: return
        // Deleting a selection removes the whole selection, not just one char.
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        // Delete the previous extended grapheme cluster, not one UTF-16 code
        // unit: emoji are surrogate pairs (or longer ZWJ sequences), and
        // deleting a single unit leaves a lone surrogate that renders as
        // U+FFFD. Capped read; clusters are never anywhere near 64 units.
        val before = textBeforeCursor(maxChars = 64)
        ic.deleteSurroundingText(precedingGraphemeLength(before), 0)
    }

    fun enter(editorInfo: EditorInfo?) {
        val ic = connectionProvider() ?: return
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    fun textBeforeCursor(maxChars: Int = 500): String? =
        connectionProvider()?.getTextBeforeCursor(maxChars, 0)?.toString()

    /** Delete [length] chars before the cursor and insert [replacement], as one edit. */
    fun replaceBeforeCursor(length: Int, replacement: String) {
        val ic = connectionProvider() ?: return
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(length, 0)
            ic.commitText(replacement, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    companion object {
        /** Pure, unit-tested: the word plus a leading space iff one is needed. */
        fun withLeadingSpace(beforeCursor: String?, word: String): String =
            if (beforeCursor.isNullOrEmpty() || beforeCursor.last().isWhitespace()) {
                word
            } else {
                " $word"
            }

        /**
         * Pure, unit-tested: should a tapped string get a leading space after
         * a swiped word? Only letters earn the space — never punctuation
         * ("time," not "time ,") and never the space bar itself (no doubles).
         */
        fun needsSpaceAfterSwipe(beforeCursor: String?, text: String): Boolean =
            text.firstOrNull()?.isLetter() == true &&
                !beforeCursor.isNullOrEmpty() &&
                !beforeCursor.last().isWhitespace()

        /**
         * Pure, unit-tested: UTF-16 length of the extended grapheme cluster
         * ending at the end of [textBeforeCursor] — i.e. how many code units
         * backspace must delete to remove one user-perceived character.
         * Uses [java.text.BreakIterator] (pure JVM, no Android deps).
         * Falls back to 1 when there is no text (or no boundary is found),
         * so callers can always pass the result straight to
         * `deleteSurroundingText`.
         *
         * Handles surrogate-pair emoji (2 units), regional-indicator flags
         * (4 units) and combining marks. ZWJ sequences (e.g. family emoji)
         * are deleted whole when the JVM's BreakIterator reports them as one
         * cluster — if it splits them, one backspace removes one sub-emoji;
         * accepted (same as many stock keyboards).
         */
        fun precedingGraphemeLength(textBeforeCursor: String?): Int {
            if (textBeforeCursor.isNullOrEmpty()) return 1
            val iterator = java.text.BreakIterator.getCharacterInstance()
            iterator.setText(textBeforeCursor)
            val boundary = iterator.preceding(textBeforeCursor.length)
            return if (boundary == java.text.BreakIterator.DONE) {
                1
            } else {
                textBeforeCursor.length - boundary
            }
        }
    }
}
