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
        ic.deleteSurroundingText(1, 0)
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
    }
}
