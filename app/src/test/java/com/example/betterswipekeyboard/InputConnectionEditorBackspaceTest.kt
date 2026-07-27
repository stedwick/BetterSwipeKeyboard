package com.example.betterswipekeyboard

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [InputConnectionEditor.backspace]'s delete-streak behavior:
 * during a held-backspace repeat the selection check (a synchronous Binder
 * round-trip) is skipped on every step but the first.
 */
class InputConnectionEditorBackspaceTest {

    /** Hand-written fake; only the methods backspace() uses are real. */
    private class FakeInputConnection(
        var selectedText: CharSequence? = null,
        var textBefore: CharSequence = "",
    ) : InputConnection {
        var getSelectedTextCalls = 0
            private set
        var deletedBefore = 0
            private set
        var committed: String? = null
            private set

        override fun getSelectedText(flags: Int): CharSequence? {
            getSelectedTextCalls++
            return selectedText
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = textBefore

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletedBefore += beforeLength
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed = text?.toString()
            return true
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException()

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = unsupported()
        override fun getCursorCapsMode(reqModes: Int): Int = unsupported()
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText =
            unsupported()

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) =
            unsupported()

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = unsupported()
        override fun setComposingRegion(start: Int, end: Int) = unsupported()
        override fun finishComposingText() = unsupported()
        override fun commitCompletion(text: CompletionInfo?) = unsupported()
        override fun commitCorrection(correctionInfo: CorrectionInfo?) = unsupported()
        override fun setSelection(start: Int, end: Int) = unsupported()
        override fun performEditorAction(editorAction: Int) = unsupported()
        override fun performContextMenuAction(id: Int) = unsupported()
        override fun beginBatchEdit() = unsupported()
        override fun endBatchEdit() = unsupported()
        override fun sendKeyEvent(event: KeyEvent?) = unsupported()
        override fun clearMetaKeyStates(states: Int) = unsupported()
        override fun reportFullscreenMode(enabled: Boolean) = unsupported()
        override fun performPrivateCommand(action: String?, data: Bundle?) = unsupported()
        override fun requestCursorUpdates(cursorUpdateMode: Int) = unsupported()
        override fun getHandler(): Handler = unsupported()
        override fun closeConnection() = unsupported()
        override fun commitContent(
            inputContentInfo: android.view.inputmethod.InputContentInfo,
            flags: Int,
            opts: Bundle?,
        ) = unsupported()
    }

    private fun editorFor(connection: InputConnection?) =
        InputConnectionEditor { connection }

    @Test
    fun `first backspace with a selection deletes the selection`() {
        val ic = FakeInputConnection(selectedText = "selected", textBefore = "abc")
        editorFor(ic).backspace()
        assertEquals("", ic.committed)
        assertEquals(0, ic.deletedBefore)
        assertEquals(1, ic.getSelectedTextCalls)
    }

    @Test
    fun `repeat backspaces skip the selection check after the first step`() {
        val ic = FakeInputConnection(textBefore = "abc")
        val editor = editorFor(ic)
        repeat(5) { editor.backspace() }
        assertEquals(1, ic.getSelectedTextCalls)
        assertEquals(5, ic.deletedBefore)
    }

    @Test
    fun `a streak that started by deleting a selection also skips the check`() {
        val ic = FakeInputConnection(selectedText = "selected", textBefore = "abc")
        val editor = editorFor(ic)
        editor.backspace()
        ic.selectedText = null // the selection is gone after commitText("")
        editor.backspace()
        assertEquals(1, ic.getSelectedTextCalls)
        assertEquals(1, ic.deletedBefore)
    }

    @Test
    fun `another edit resets the streak`() {
        val ic = FakeInputConnection(textBefore = "abc")
        val editor = editorFor(ic)
        editor.backspace()
        editor.commitText("x")
        editor.backspace()
        assertEquals(2, ic.getSelectedTextCalls)
    }

    @Test
    fun `backspace deletes the whole grapheme cluster`() {
        val ic = FakeInputConnection(textBefore = "text😀")
        editorFor(ic).backspace()
        assertEquals(2, ic.deletedBefore)
    }

    @Test
    fun `null connection is a no-op`() {
        editorFor(null).backspace() // must not throw
        assertNull(null)
    }
}
