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
}
