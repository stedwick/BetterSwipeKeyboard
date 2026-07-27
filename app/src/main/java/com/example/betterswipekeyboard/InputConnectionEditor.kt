package com.example.betterswipekeyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.abs

/**
 * The only class that talks to the text field. It resolves the
 * [InputConnection] lazily because the connection changes every time focus
 * moves between fields.
 */
class InputConnectionEditor(
    private val connectionProvider: () -> InputConnection?,
) {
    /**
     * True while consecutive [backspace] calls run with no other edit in
     * between. During such a streak (the held-backspace repeat) the
     * selection check is skipped: either there was no selection when the
     * streak started, or the first backspace already deleted it — a new
     * selection cannot appear while the finger stays on the key. Skipping
     * it removes one synchronous Binder round-trip per repeat step. Reset
     * by every other editing method.
     */
    private var deleteStreak = false

    fun commitText(text: String) {
        deleteStreak = false
        connectionProvider()?.commitText(text, 1)
    }

    /**
     * Commits a swiped word, inserting a leading space when the text before
     * the cursor doesn't already end in whitespace (and never at the very
     * start of the field).
     */
    fun commitWord(word: String) {
        deleteStreak = false
        val before = textBeforeCursor(maxChars = 1)
        commitText(withLeadingSpace(before, word))
    }

    fun backspace() {
        val ic = connectionProvider() ?: return
        // Deleting a selection removes the whole selection, not just one char.
        if (!deleteStreak && !ic.getSelectedText(0).isNullOrEmpty()) {
            deleteStreak = true
            ic.commitText("", 1)
            return
        }
        deleteStreak = true
        // Delete the previous extended grapheme cluster, not one UTF-16 code
        // unit: emoji are surrogate pairs (or longer ZWJ sequences), and
        // deleting a single unit leaves a lone surrogate that renders as
        // U+FFFD. Capped read; clusters are never anywhere near 64 units.
        val before = textBeforeCursor(maxChars = 64)
        ic.deleteSurroundingText(precedingGraphemeLength(before), 0)
    }

    /**
     * Deletes the word a swipe just committed (KeyboardEffect
     * .DeleteWordBackward): one read of the text before the cursor, one
     * delete — the same minimal Binder traffic as a char [backspace]. A
     * live selection wins, as in [backspace]: the selection IS what the
     * user wants gone, and its presence means the cursor context changed
     * since the swipe. Joins the delete streak either way, so a
     * held-backspace repeat (first step word, rest chars) skips the
     * selection check on the char steps.
     */
    fun deleteWordBackward() {
        val ic = connectionProvider() ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            deleteStreak = true
            ic.commitText("", 1)
            return
        }
        deleteStreak = true
        // Capped read; swiped words are nowhere near this long. A clipped
        // window degrades to deleting a word suffix, never wrong text.
        val before = textBeforeCursor(maxChars = WORD_DELETE_MAX_CHARS)
        ic.deleteSurroundingText(precedingWordLength(before), 0)
    }

    fun enter(editorInfo: EditorInfo?) {
        deleteStreak = false
        val ic = connectionProvider() ?: return
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    /**
     * Moves the cursor by [steps] characters (negative = backward) via
     * D-pad key events: the target app handles grapheme clusters (no
     * mid-surrogate cursor stops), selection collapse and boundary
     * clamping — the same path as hardware arrow keys. A setSelection
     * implementation would need ExtractedText offset plumbing and manual
     * grapheme math for the same guarantees.
     */
    fun moveCursor(steps: Int) {
        deleteStreak = false
        val ic = connectionProvider() ?: return
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    fun textBeforeCursor(maxChars: Int = 500): String? =
        connectionProvider()?.getTextBeforeCursor(maxChars, 0)?.toString()

    /** Delete [length] chars before the cursor and insert [replacement], as one edit. */
    fun replaceBeforeCursor(length: Int, replacement: String) {
        deleteStreak = false
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
        /** How much text [deleteWordBackward] reads to measure the word. */
        private const val WORD_DELETE_MAX_CHARS = 128

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

        /**
         * Pure, unit-tested: UTF-16 length of the trailing word at the end
         * of [textBeforeCursor], plus the run of spaces/tabs between it and
         * the previous word — but never a newline. Swipe commits
         * auto-insert one leading space ([withLeadingSpace]: "hello" +
         * swipe → "hello world"), so consuming the space(s) returns the
         * cursor to the pre-swipe state ("hello"). A newline is always
         * user-typed — commitWord adds no space after whitespace — and must
         * survive the word delete ("hello\nworld" → "hello\n").
         *
         * Boundaries come from [java.text.BreakIterator]'s word instance
         * (pure JVM, no Android deps), which never splits a surrogate pair
         * or grapheme cluster — a trailing emoji is deleted whole or left
         * alone, never halved into a U+FFFD. Returns 0 when there is
         * nothing to delete, so callers can pass the result straight to
         * `deleteSurroundingText`.
         */
        fun precedingWordLength(textBeforeCursor: String?): Int {
            if (textBeforeCursor.isNullOrEmpty()) return 0
            val iterator = java.text.BreakIterator.getWordInstance()
            iterator.setText(textBeforeCursor)
            var start = iterator.preceding(textBeforeCursor.length)
            if (start == java.text.BreakIterator.DONE) return 0
            while (start > 0 &&
                (textBeforeCursor[start - 1] == ' ' || textBeforeCursor[start - 1] == '\t')
            ) {
                start--
            }
            return textBeforeCursor.length - start
        }
    }
}
